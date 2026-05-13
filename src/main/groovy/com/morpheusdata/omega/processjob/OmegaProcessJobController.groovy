package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission
import com.morpheusdata.model.Process as ProcessModel
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.User
import com.morpheusdata.model.process.InsertProcessStepRequest
import com.morpheusdata.model.process.InsertProcessStepResponse
import com.morpheusdata.model.process.ProcessJobExecutionRequest
import com.morpheusdata.model.process.RunProcessStepRequest
import com.morpheusdata.model.process.RunProcessStepResponse
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * Plugin controller that exposes REST endpoints for exercising OmegaProcessJobProvider
 * workflows through the Morpheus HTTP server via ControllerProvider/PluginController.
 *
 * Endpoints (all under /plugin/process-jobs/...):
 *   POST /plugin/process-jobs/execute           — directly invoke OmegaProcessJobProvider.execute()
 *   POST /plugin/process-jobs/start-process     — start a standalone process on an existing workload
 *   POST /plugin/process-jobs/run               — dispatch a process step for execution
 *   POST /plugin/process-jobs/retry             — retry a failed process step
 *   GET  /plugin/process-jobs/status            — get status of a process and its events
 *   GET  /plugin/process-jobs/health            — simple health check
 *
 * @since 0.4.0
 */
@Slf4j
class OmegaProcessJobController implements PluginController {

	private MorpheusContext morpheusContext
	private Plugin plugin
	private OmegaProcessJobProvider processJobProvider

	OmegaProcessJobController(Plugin plugin, MorpheusContext morpheusContext, OmegaProcessJobProvider processJobProvider) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
		this.processJobProvider = processJobProvider
	}

	@Override
	List<Route> getRoutes() {
		return [
			Route.build('/process-jobs/execute', 'execute', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/start-process', 'startProcess', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/add-steps', 'addSteps', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/run', 'run', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/retry', 'retry', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/status', 'status', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/health', 'health', Permission.build('admin-appliance', 'full')),
		]
	}

	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return plugin
	}

	@Override
	String getCode() {
		return 'omega-process-job-controller'
	}

	@Override
	String getName() {
		return 'Omega Process Job Controller'
	}

	/**
	 * POST /plugin/process-jobs/execute
	 * Directly invokes OmegaProcessJobProvider.execute() with given config.
	 */
	def execute(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			def request = new ProcessJobExecutionRequest()
			request.processEventId = body.processEventId as Long ?: 0L
			request.opts = body

			ServiceResponse result = processJobProvider.execute(request)
			return JsonResponse.of([
				success: result.success,
				msg    : result.msg,
				data   : result.data ? [nextOpts: result.data.nextOpts] : null
			])
		} catch (e) {
			return errorResponse(e.message)
		}
	}

	/**
	 * POST /plugin/process-jobs/start-process
	 * Starts a standalone process on a pre-existing workload via
	 * MorpheusProcessService#startProcess(), then inserts one or more omega
	 * process job steps so the process is immediately usable.
	 */
	def startProcess(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long workloadId = body.workloadId as Long
			Long userId = (body.userId as Long) ?: 1L
			String description = (body.description as String) ?: 'Omega dummy process'
			String stepTypeCode = (body.stepTypeCode as String) ?: 'general'
			List stepConfigs = (body.stepConfigs as List) ?: []
			Integer stepCount = body.containsKey('stepCount') ? (body.stepCount as Integer) : stepConfigs.size()

			if (!workloadId) {
				def resp = JsonResponse.of([success: false, msg: 'workloadId is required'])
				resp.status = 400
				return resp
			}

			def workload = morpheusContext.services.workload.get(workloadId)
			if (!workload) {
				def resp = JsonResponse.of([success: false, msg: "Workload ${workloadId} not found"])
				resp.status = 404
				return resp
			}

			User user = morpheusContext.services.admin.user.get(userId)
			if (!user) {
				def resp = JsonResponse.of([success: false, msg: "User ${userId} not found"])
				resp.status = 404
				return resp
			}

			ProcessModel process = morpheusContext.services.process.startProcess(
				workload,
				ProcessStepType.forCode(stepTypeCode),
				user,
				description
			)

			// Insert omega process job steps into the new process
			def steps = []
			if (process) {
				def processService = morpheusContext.services.process
				for (int i = 0; i < stepCount; i++) {
					def config = (i < stepConfigs.size() ? stepConfigs[i] : null) as Map
					config = config ?: [sleepSeconds: 2, outputMessage: "Step ${i + 1} complete"]

					def stepEvent = new ProcessEvent()
					stepEvent.stepType = ProcessStepType.forCode(stepTypeCode)
					stepEvent.eventTitle = "Omega Step ${i + 1}"
					stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
					stepEvent.jobConfig = config

					def insertRequest = new InsertProcessStepRequest(process, stepEvent)
					InsertProcessStepResponse insertResponse = processService.insertProcessStep(insertRequest)
					steps << [eventId: insertResponse?.processEventId, stepTitle: stepEvent.eventTitle]
				}
			}

			return JsonResponse.of([
				success  : process != null,
				processId: process?.id,
				steps    : steps,
				msg      : "Process started for workload ${workloadId} with ${stepCount} step(s)"
			])
		} catch (e) {
			return errorResponse(e.message)
		}
	}

	/**
	 * POST /plugin/process-jobs/add-steps
	 * Adds one or more steps to an existing process.
	 */
	def addSteps(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long processId = body.processId as Long
			List stepConfigs = (body.stepConfigs as List) ?: []
			String stepTypeCode = (body.stepTypeCode as String) ?: 'general'

			if (!processId) {
				def resp = JsonResponse.of([success: false, msg: 'processId is required'])
				resp.status = 400
				return resp
			}
			if (!stepConfigs) {
				def resp = JsonResponse.of([success: false, msg: 'stepConfigs is required'])
				resp.status = 400
				return resp
			}

			def processModel = new ProcessModel()
			processModel.id = processId

			def processService = morpheusContext.services.process
			def steps = []
			stepConfigs.eachWithIndex { cfg, i ->
				def config = (cfg as Map) ?: [sleepSeconds: 5]

				def stepEvent = new ProcessEvent()
				stepEvent.stepType = ProcessStepType.forCode(stepTypeCode)
				stepEvent.eventTitle = (config.stepTitle as String) ?: "Omega Step ${i + 1}"
				stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
				stepEvent.jobConfig = config

				def insertRequest = new InsertProcessStepRequest(processModel, stepEvent)
				InsertProcessStepResponse insertResponse = processService.insertProcessStep(insertRequest)
				steps << [eventId: insertResponse?.processEventId, stepTitle: stepEvent.eventTitle]
			}

			return JsonResponse.of([
				success  : true,
				processId: processId,
				steps    : steps,
				msg      : "Added ${steps.size()} step(s) to process ${processId}"
			])
		} catch (e) {
			return errorResponse(e.message)
		}
	}

	/**
	 * POST /plugin/process-jobs/run
	 * Dispatches a process step for execution via the normal pipeline.
	 */
	def run(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long processId = body.processId as Long
			Long eventId = body.eventId as Long

			if (!processId || !eventId) {
				def resp = JsonResponse.of([success: false, msg: 'processId and eventId are required'])
				resp.status = 400
				return resp
			}

			def processModel = new ProcessModel()
			processModel.id = processId

			def eventModel = new ProcessEvent()
			eventModel.id = eventId

			def request = new RunProcessStepRequest(processModel, eventModel)
			RunProcessStepResponse response = morpheusContext.services.process.runProcessStep(request)

			return JsonResponse.of([success: response?.success ?: false])
		} catch (e) {
			return errorResponse(e.message)
		}
	}

	/**
	 * POST /plugin/process-jobs/retry
	 * Retries a failed process step — resets status and dispatches via pipeline.
	 */
	def retry(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long processId = body.processId as Long
			Long eventId = body.eventId as Long

			if (!processId || !eventId) {
				def resp = JsonResponse.of([success: false, msg: 'processId and eventId are required'])
				resp.status = 400
				return resp
			}

			// Store input overrides in the provider so they're applied on next execute()
			Map inputs = (body.inputs as Map) ?: (body.config as Map)
			if (inputs) {
				log.info("Storing overrides for eventId ${eventId} (type: ${eventId.getClass()}): ${inputs}")
				processJobProvider.eventConfigOverrides[eventId] = inputs
				log.info("Override map now has keys: ${processJobProvider.eventConfigOverrides.keySet()} (types: ${processJobProvider.eventConfigOverrides.keySet().collect{it.getClass()}})")
			}

			def processModel = new ProcessModel()
			processModel.id = processId

			def eventModel = new ProcessEvent()
			eventModel.id = eventId

			def request = new RunProcessStepRequest(processModel, eventModel)
			RunProcessStepResponse response = morpheusContext.services.process.runProcessStep(request)

			return JsonResponse.of([success: response?.success ?: false])
		} catch (e) {
			return errorResponse(e.message)
		}
	}

	/**
	 * GET /plugin/process-jobs/status?processId=123
	 * Returns process and event status information.
	 */
	def status(ViewModel<Map> model) {
		try {
			Map params = model.object ?: [:]
			Long processId = params.processId as Long

			if (!processId) {
				def resp = JsonResponse.of([success: false, msg: 'processId query param is required'])
				resp.status = 400
				return resp
			}

			def process = morpheusContext.services.process.get(processId)
			if (!process) {
				def resp = JsonResponse.of([success: false, msg: 'Process not found'])
				resp.status = 404
				return resp
			}

			return JsonResponse.of([
				success  : true,
				processId: process.id,
				process  : [
					id      : process.id,
					stepType: process.stepType?.code
				]
			])
		} catch (e) {
			return errorResponse(e.message)
		}
	}

	/**
	 * GET /plugin/process-jobs/health
	 * Simple health check endpoint.
	 */
	def health(ViewModel<Map> model) {
		return JsonResponse.of([status: 'ok', provider: OmegaProcessJobProvider.PROVIDER_CODE])
	}

	// --- Utility ---

	private static JsonResponse errorResponse(String msg) {
		def resp = JsonResponse.of([success: false, msg: msg])
		resp.status = 500
		return resp
	}
}
