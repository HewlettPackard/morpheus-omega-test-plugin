package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.model.OperationData
import com.morpheusdata.model.Permission
import com.morpheusdata.model.Process as ProcessModel
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.User
import com.morpheusdata.model.process.InsertProcessStepRequest
import com.morpheusdata.model.process.InsertProcessStepResponse
import com.morpheusdata.model.process.RunProcessStepRequest
import com.morpheusdata.model.process.RunProcessStepResponse
import com.morpheusdata.model.system.System
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route
import groovy.util.logging.Slf4j

@Slf4j
class OmegaParallelProcessController implements PluginController {

	static final String HIERARCHY_CATEGORY = 'omega.process.hierarchy'
	static final String HIERARCHY_REF_TYPE = 'Process'
	static final String DEFAULT_SCENARIO_NAME = 'Provisioning System omega-test-001'

	private final MorpheusContext morpheusContext
	private final Plugin plugin

	OmegaParallelProcessController(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	List<Route> getRoutes() {
		return [
			Route.build('/process-jobs/start-parallel-processes', 'startParallelProcesses', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/parallel-status', 'parallelStatus', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/parallel-clear', 'parallelClear', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/parallel-trigger-rollback', 'parallelTriggerRollback', Permission.build('admin-appliance', 'full')),
			Route.build('/process-jobs/start-single-hierarchy', 'startSingleHierarchy', Permission.build('admin-appliance', 'full'))
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
		return 'omega-parallel-process-controller'
	}

	@Override
	String getName() {
		return 'Omega Parallel Process Controller'
	}

	def startParallelProcesses(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long systemId = body.systemId as Long
			String scenarioName = (body.scenarioName as String) ?: DEFAULT_SCENARIO_NAME

			if (!systemId) {
				return badRequest('systemId is required')
			}

			System system = morpheusContext.services.system.get(systemId)
			if (!system) {
				return notFound("System ${systemId} not found")
			}

			User user = resolveUser(body.userId as Long)
			if (!user) {
				return notFound('Unable to resolve the requested user')
			}

			Map result = createHierarchy(system, user, scenarioName, true, true)
			if (result.success != true) {
				return errorResponse(result.msg as String)
			}

			return JsonResponse.of([
				success   : true,
				scenario  : scenarioName,
				systemId  : systemId,
				parentId  : result.parentId,
				childCount: (result.children as List)?.size() ?: 0,
				children  : result.children,
				msg       : "Started example parallel process hierarchy for system ${systemId}"
			])
		} catch (Exception e) {
			log.error('Error starting example parallel processes', e)
			return errorResponse(e.message)
		}
	}

	def startSingleHierarchy(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long systemId = body.systemId as Long
			String scenarioName = (body.scenarioName as String) ?: 'Manual Parallel Hierarchy'

			if (!systemId) {
				return badRequest('systemId is required')
			}

			System system = morpheusContext.services.system.get(systemId)
			if (!system) {
				return notFound("System ${systemId} not found")
			}

			User user = resolveUser(body.userId as Long)
			if (!user) {
				return notFound('Unable to resolve the requested user')
			}

			Map result = createHierarchy(system, user, scenarioName, false, false)
			if (result.success != true) {
				return errorResponse(result.msg as String)
			}

			return JsonResponse.of([
				success   : true,
				scenario  : scenarioName,
				systemId  : systemId,
				parentId  : result.parentId,
				childCount: (result.children as List)?.size() ?: 0,
				children  : result.children,
				msg       : "Created manual hierarchy for system ${systemId} without dispatching steps"
			])
		} catch (Exception e) {
			log.error('Error creating manual hierarchy', e)
			return errorResponse(e.message)
		}
	}

	/**
	 * POST /plugin/process-jobs/parallel-clear
	 * Deletes all OperationData hierarchy links for a given system, resetting the tab to initial state.
	 */
	def parallelClear(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long systemId = body.systemId as Long

			if (!systemId) {
				return badRequest('systemId is required')
			}

			String systemPath = systemPath(systemId)
			List<OperationData> links = (morpheusContext.services.operationData.list(new DataQuery().withFilter('category', HIERARCHY_CATEGORY)) ?: [])
				.findAll { OperationData link ->
					link?.path == systemPath || link?.description?.contains("system ${systemId}")
				}

			if (links) {
				morpheusContext.services.operationData.remove(links)
			}

			return JsonResponse.of([
				success: true,
				removed: links.size(),
				msg    : "Cleared ${links.size()} hierarchy link(s) for system ${systemId}"
			])
		} catch (Exception e) {
			log.error('Error clearing parallel process history', e)
			return errorResponse(e.message)
		}
	}

	/**
	 * POST /plugin/process-jobs/parallel-trigger-rollback
	 * Adds a rollback processEvent to the given process and dispatches it.
	 * The failed event is left as-is (already in 'failed' state).
	 */
	def parallelTriggerRollback(ViewModel<Map> model) {
		try {
			Map body = model.object ?: [:]
			Long processId = body.processId as Long
			Long systemId = body.systemId as Long
			String rollbackTitle = (body.rollbackTitle as String) ?: 'Rollback: Reverting failed operation'

			if (!processId) {
				return badRequest('processId is required')
			}

			ProcessModel processRef = new ProcessModel(id: processId)

			// Insert a rollback step (no-op that succeeds immediately)
			Map<String, Object> rollbackConfig = [
				processId    : processId,
				systemId     : systemId,
				sleepSeconds : 1,
				outputMessage: "${rollbackTitle} - no-op (simulated rollback complete)",
				simulateFailure: 'false',
				rollbackStep : 'true'
			]

			ProcessEvent rollbackEvent = new ProcessEvent()
			rollbackEvent.stepType = ProcessStepType.forCode('general')
			rollbackEvent.eventTitle = rollbackTitle
			rollbackEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
			rollbackEvent.jobConfig = rollbackConfig

			InsertProcessStepResponse insertResponse = morpheusContext.services.process.insertProcessStep(new InsertProcessStepRequest(processRef, rollbackEvent))
			Long rollbackEventId = insertResponse?.processEventId

			// Dispatch the rollback step
			if (rollbackEventId) {
				ProcessEvent dispatchEvent = new ProcessEvent(id: rollbackEventId)
				morpheusContext.services.process.runProcessStep(new RunProcessStepRequest(processRef, dispatchEvent))
			}

			return JsonResponse.of([
				success       : true,
				processId     : processId,
				rollbackEventId: rollbackEventId,
				msg           : "Rollback step '${rollbackTitle}' added and dispatched for process ${processId}"
			])
		} catch (Exception e) {
			log.error('Error triggering rollback', e)
			return errorResponse(e.message)
		}
	}

	def parallelStatus(ViewModel<Map> model) {
		try {
			Map params = model.object ?: [:]
			Long systemId = params.systemId as Long

			if (!systemId) {
				return badRequest('systemId query param is required')
			}

			String systemPath = systemPath(systemId)
			List<OperationData> links = (morpheusContext.services.operationData.list(new DataQuery().withFilter('category', HIERARCHY_CATEGORY)) ?: [])
				.findAll { OperationData link ->
					link?.path == systemPath || link?.description?.contains("system ${systemId}")
				}

			// Build a map of parentId -> list of child link records
			Map<String, List<OperationData>> grouped = links.groupBy { OperationData link -> link?.keyValue }

			// Determine top-level parents: those that are NOT themselves a child of another process
			Set<String> allChildIds = links.collect { it?.refId }.findAll { it != null }.toSet()
			Set<String> topLevelParentIds = grouped.keySet().findAll { String parentId -> !allChildIds.contains(parentId) }

			List<Map> hierarchies = topLevelParentIds.collect { String parentProcessId ->
				Long parentId = safeLong(parentProcessId)
				ProcessModel parentProcess = parentId ? morpheusContext.services.process.get(parentId) : null
				if (!parentProcess) return null

				return [
					parent  : serializeProcess(parentProcess, null),
					children: buildChildTreeSerialized(parentProcessId, grouped)
				]
			}.findAll { it != null }

			return JsonResponse.of([
				success    : true,
				systemId   : systemId,
				hierarchies: hierarchies
			])
		} catch (Exception e) {
			log.error('Error fetching parallel process status', e)
			return errorResponse(e.message)
		}
	}

	/**
	 * Recursively builds the child tree from OperationData links.
	 * Each child node contains its id and any grandchildren (children of children).
	 */
	private List<Map> buildChildTree(String parentId, Map<String, List<OperationData>> grouped) {
		List<OperationData> childLinks = grouped[parentId] ?: []
		return childLinks.collect { OperationData link ->
			Long childId = safeLong(link?.refId)
			if (!childId) return null
			String childIdStr = String.valueOf(childId)
			List<Map> grandchildren = buildChildTree(childIdStr, grouped)
			return [
				id      : childId,
				name    : link?.name,
				children: grandchildren
			]
		}.findAll { it != null }
	}

	/**
	 * Recursively builds the serialized child tree with full process details.
	 */
	private List<Map> buildChildTreeSerialized(String parentId, Map<String, List<OperationData>> grouped) {
		List<OperationData> childLinks = grouped[parentId] ?: []
		return childLinks.collect { OperationData link ->
			Long childId = safeLong(link?.refId)
			if (!childId) return null
			ProcessModel childProcess = morpheusContext.services.process.get(childId)
			if (!childProcess) return null
			String childIdStr = String.valueOf(childId)
			Map serialized = serializeProcess(childProcess, link)
			serialized.children = buildChildTreeSerialized(childIdStr, grouped)
			return serialized
		}.findAll { it != null }
	}

	private Map serializeProcess(ProcessModel process, OperationData link) {
		List<Map> events = ((process?.processEvents ?: []) as List)
			.collect { ProcessEvent event -> serializeEvent(event, process?.id) }
			.sort { Map left, Map right -> ((left.id ?: 0L) as Long) <=> ((right.id ?: 0L) as Long) }

		// Prefer eventTitle as the primary label (matches standard Morpheus history behavior)
		String label = process?.eventTitle ?: link?.name ?: process?.displayName ?: process?.name ?: "Process ${process?.id}"

		return [
			id         : process?.id,
			displayName: label,
			status     : process?.status ?: 'pending',
			message    : process?.message,
			error      : process?.error,
			startDate  : process?.startDate?.format("yyyy-MM-dd'T'HH:mm:ssXXX"),
			endDate    : process?.endDate?.format("yyyy-MM-dd'T'HH:mm:ssXXX"),
			durationMs : computeDurationMs(process?.startDate, process?.endDate),
			events     : events
		]
	}

	private Map serializeEvent(ProcessEvent event, Long processId) {
		String title = event?.eventTitle ?: event?.name ?: "Step ${event?.id}"
		Boolean rollback = title.toLowerCase().startsWith('rollback:') || title.toLowerCase().startsWith('rollback ')
		return [
			id        : event?.id,
			processId : processId,
			eventTitle: title,
			status    : event?.status ?: 'pending',
			message   : event?.message,
			output    : event?.output,
			error     : event?.error,
			startDate : event?.startDate?.format("yyyy-MM-dd'T'HH:mm:ssXXX"),
			endDate   : event?.endDate?.format("yyyy-MM-dd'T'HH:mm:ssXXX"),
			durationMs: computeDurationMs(event?.startDate, event?.endDate),
			rollback  : rollback,
			retryable : event?.retryable ?: false,
			cancelable: event?.cancelable ?: false
		]
	}

	private static Long computeDurationMs(Date startDate, Date endDate) {
		if (!startDate) return null
		Date finish = endDate ?: new Date()
		return Math.max(0L, finish.time - startDate.time)
	}

	private Map createHierarchy(System system, User user, String scenarioName, Boolean autoRunSteps, Boolean completeParent) {
		ServiceResponse<ProcessModel> parentResponse = morpheusContext.services.process.startProcess(
			system,
			ProcessStepType.forCode('general'),
			user,
			'omega.parallel',
			scenarioName
		)
		if (!parentResponse?.success || !parentResponse?.data) {
			return [success: false, msg: parentResponse?.msg ?: 'Failed to start parent process']
		}

		ProcessModel parentProcess = parentResponse.data
		List<Map> childDefinitions = buildExampleScenario()
		List<Map> createdChildren = []

		childDefinitions.each { Map definition ->
			Map childResult = createChildProcess(system, user, parentProcess, scenarioName, definition, autoRunSteps)
			createdChildren << childResult
		}

		if (completeParent) {
			String completionMessage = "Created ${createdChildren.size()} child processes for ${scenarioName}"
			try {
				morpheusContext.services.process.updateProcessMessage(parentProcess, system.id as Long, completionMessage)
			} catch (Exception ignored) {
				log.debug('Unable to update parent process message before completing hierarchy', ignored)
			}
			morpheusContext.services.process.endProcess(parentProcess, 'complete', completionMessage)
		}

		return [
			success : true,
			parentId: parentProcess.id,
			children: createdChildren
		]
	}

	private Map createChildProcess(System system, User user, ProcessModel parentProcess, String scenarioName, Map definition, Boolean autoRunSteps) {
		String stepTypeCode = (definition.stepTypeCode as String) ?: 'general'
		ServiceResponse<ProcessModel> childResponse = morpheusContext.services.process.startProcess(
			system,
			ProcessStepType.forCode(stepTypeCode),
			user,
			'omega.parallel.child',
			definition.name as String
		)
		if (!childResponse?.success || !childResponse?.data) {
			throw new IllegalStateException(childResponse?.msg ?: "Failed to start child process ${definition.name}")
		}

		ProcessModel childProcess = childResponse.data
		createHierarchyLink(system, parentProcess, childProcess, definition.name as String, scenarioName)

		ProcessModel processRef = new ProcessModel(id: childProcess.id)
		List<Map> insertedSteps = []
		(definition.steps as List<Map>).each { Map stepDefinition ->
			Map<String, Object> jobConfig = buildJobConfig(system, parentProcess, childProcess, stepDefinition)

			ProcessEvent processEvent = new ProcessEvent()
			processEvent.stepType = ProcessStepType.forCode((stepDefinition.stepTypeCode as String) ?: stepTypeCode)
			processEvent.eventTitle = stepDefinition.title as String
			processEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
			processEvent.jobConfig = jobConfig

			InsertProcessStepResponse insertResponse = morpheusContext.services.process.insertProcessStep(new InsertProcessStepRequest(processRef, processEvent))
			Long processEventId = insertResponse?.processEventId
			insertedSteps << [
				eventId   : processEventId,
				eventTitle: processEvent.eventTitle,
				autoRun   : stepDefinition.autoRun != false,
				rollback  : stepDefinition.rollback == true
			]
		}

		if (autoRunSteps) {
			// Only dispatch the first step — the process job pipeline handles sequential execution.
			// Dispatching all steps at once would cause them to run in parallel.
			Map firstStep = insertedSteps.find { Map step -> step.autoRun == true && step.rollback != true }
			if (firstStep) {
				ProcessEvent processEvent = new ProcessEvent(id: firstStep.eventId as Long)
				RunProcessStepResponse response = morpheusContext.services.process.runProcessStep(new RunProcessStepRequest(processRef, processEvent))
				log.debug('Dispatched first process event {} for child process {} (success={})', firstStep.eventId, childProcess.id, response?.success)
			}
		}

		// Create grandchildren (nested child processes)
		List<Map> createdGrandchildren = []
		List<Map> grandchildDefinitions = (definition.children as List<Map>) ?: []
		grandchildDefinitions.each { Map grandchildDef ->
			Map grandchildResult = createChildProcess(system, user, childProcess, scenarioName, grandchildDef, autoRunSteps)
			createdGrandchildren << grandchildResult
		}

		return [
			processId  : childProcess.id,
			displayName: definition.name,
			steps      : insertedSteps,
			children   : createdGrandchildren
		]
	}

	private void createHierarchyLink(System system, ProcessModel parentProcess, ProcessModel childProcess, String childName, String scenarioName) {
		OperationData operationData = new OperationData()
		operationData.code = "omega.process.hierarchy.${parentProcess.id}.${childProcess.id}"
		operationData.category = HIERARCHY_CATEGORY
		operationData.name = childName
		operationData.keyValue = String.valueOf(parentProcess.id)
		operationData.value = scenarioName
		operationData.refType = HIERARCHY_REF_TYPE
		operationData.refId = String.valueOf(childProcess.id)
		operationData.description = "Parallel process hierarchy for system ${system.id}: parent ${parentProcess.id} -> child ${childProcess.id}"
		operationData.path = systemPath(system.id as Long)
		operationData.status = 'linked'
		operationData.enabled = true
		morpheusContext.services.operationData.create(operationData)
	}

	private Map<String, Object> buildJobConfig(System system, ProcessModel parentProcess, ProcessModel childProcess, Map stepDefinition) {
		Map<String, Object> config = [
			processId         : childProcess.id,
			systemId          : system.id,
			hierarchyParentId : parentProcess.id,
			hierarchyChildId  : childProcess.id,
			sleepSeconds      : (stepDefinition.sleepSeconds ?: 1) as Integer,
			outputMessage     : stepDefinition.outputMessage,
			stepOutput        : stepDefinition.stepOutput ?: stepDefinition.outputMessage,
			stepError         : stepDefinition.stepError,
			simulateFailure   : stepDefinition.simulateFailure == true ? 'true' : 'false',
			isRetryable       : stepDefinition.isRetryable == false ? 'false' : 'true',
			retryCount        : (stepDefinition.retryCount ?: 0) as Integer,
			isCancelable      : stepDefinition.isCancelable == false ? 'false' : 'true',
			succeedOnAttempt  : (stepDefinition.succeedOnAttempt ?: 0) as Integer,
			rollbackStep      : stepDefinition.rollback == true ? 'true' : 'false'
		]
		return config.findAll { String key, Object value -> value != null }
	}

	private List<Map> buildExampleScenario() {
		return [
			[
				name        : 'Configuring Networking',
				stepTypeCode: 'provisionNetwork',
				steps       : [
					[
						title        : 'Allocating IP Address',
						outputMessage: 'Allocated 10.0.1.100',
						sleepSeconds : 1
					],
					[
						title        : 'Configuring DNS Records',
						outputMessage: 'DNS A record created: omega-test-001.example.com → 10.0.1.100',
						sleepSeconds : 1
					],
					[
						title        : 'Setting Up Firewall Rules',
						outputMessage: 'Opened ports: 22, 80, 443',
						sleepSeconds : 1
					]
				],
				children    : [
					[
						name        : 'Configuring VLAN Tagging',
						stepTypeCode: 'provisionNetwork',
						steps       : [
							[
								title        : 'Creating VLAN Interface',
								outputMessage: 'Created VLAN 100 on eth0 (eth0.100)',
								sleepSeconds : 1
							],
							[
								title        : 'Applying Trunk Configuration',
								outputMessage: 'Trunk port configured: allowed VLANs 100,200,300',
								sleepSeconds : 1
							],
							[
								title        : 'Verifying Connectivity',
								outputMessage: 'VLAN 100 reachable: ping gateway 10.100.0.1 OK',
								sleepSeconds : 1
							]
						]
					]
				]
			],
			[
				name        : 'Configuring Storage',
				stepTypeCode: 'provisionVolumes',
				steps       : [
					[
						title        : 'Creating Volume',
						outputMessage: 'Created 100GB volume vol-abc123',
						sleepSeconds : 1
					],
					[
						title          : 'Mounting Filesystem',
						outputMessage  : 'Attempting to mount /data',
						stepError      : 'Mount point /data is busy - device or resource busy',
						simulateFailure: true,
						sleepSeconds   : 1,
						retryCount     : 0
					]
				]
			],
			[
				name        : 'Installing Software',
				stepTypeCode: 'installSoftware',
				steps       : [
					[
						title        : 'Downloading Packages',
						outputMessage: 'Downloaded 47 packages (156MB)',
						sleepSeconds : 1
					],
					[
						title        : 'Installing Dependencies',
						outputMessage: 'Installed: nginx 1.24, postgresql 16, redis 7.2',
						sleepSeconds : 1
					],
					[
						title        : 'Configuring Services',
						outputMessage: 'Services configured and enabled: nginx, postgresql, redis',
						sleepSeconds : 1
					]
				]
			]
		]
	}

	private User resolveUser(Long userId) {
		User user = userId ? morpheusContext.services.admin.user.get(userId) : null
		if (!user) {
			user = morpheusContext.services.admin.user.get(1L)
		}
		return user
	}

	private static Long safeLong(def value) {
		try {
			return value == null ? null : (value as Long)
		} catch (Exception ignored) {
			return null
		}
	}

	private static String systemPath(Long systemId) {
		return "system:${systemId}"
	}

	private static JsonResponse badRequest(String msg) {
		def resp = JsonResponse.of([success: false, msg: msg])
		resp.status = 400
		return resp
	}

	private static JsonResponse notFound(String msg) {
		def resp = JsonResponse.of([success: false, msg: msg])
		resp.status = 404
		return resp
	}

	private static JsonResponse errorResponse(String msg) {
		def resp = JsonResponse.of([success: false, msg: msg])
		resp.status = 500
		return resp
	}
}
