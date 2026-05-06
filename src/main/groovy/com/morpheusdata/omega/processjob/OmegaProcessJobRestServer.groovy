package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Process as ProcessModel
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.Workload
import com.morpheusdata.model.process.InsertProcessStepRequest
import com.morpheusdata.model.process.InsertProcessStepResponse
import com.morpheusdata.model.process.ProcessJobExecutionRequest
import com.morpheusdata.model.process.RunProcessStepRequest
import com.morpheusdata.model.process.RunProcessStepResponse
import com.morpheusdata.response.ServiceResponse
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Lightweight HTTP server embedded in the omega test plugin to exercise
 * OmegaProcessJobProvider workflows via REST calls. Binds to 127.0.0.1:8090.
 *
 * Endpoints:
 *   POST /process-jobs/execute           — directly invoke OmegaProcessJobProvider.execute()
 *   POST /process-jobs/create            — create a process with a single omega job step
 *   POST /process-jobs/create-multi-step — create a process with multiple omega job steps
 *   POST /process-jobs/run               — dispatch a process step for execution
 *   POST /process-jobs/retry             — retry a failed process step
 *   GET  /process-jobs/status            — get status of a process and its events
 *
 * @since 0.4.0
 */
class OmegaProcessJobRestServer {

	private static final int PORT = 8090
	private HttpServer server
	private final MorpheusContext morpheusContext
	private final Plugin plugin
	private final OmegaProcessJobProvider processJobProvider
	private final JsonSlurper jsonSlurper = new JsonSlurper()

	OmegaProcessJobRestServer(Plugin plugin, MorpheusContext morpheusContext, OmegaProcessJobProvider processJobProvider) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
		this.processJobProvider = processJobProvider
	}

	void start() {
		try {
			server = HttpServer.create(new InetSocketAddress('127.0.0.1', PORT), 0)
			server.executor = Executors.newFixedThreadPool(4)

			server.createContext('/process-jobs/execute', new ExecuteHandler())
			server.createContext('/process-jobs/create-multi-step', new CreateMultiStepHandler())
			server.createContext('/process-jobs/create', new CreateHandler())
			server.createContext('/process-jobs/run', new RunHandler())
			server.createContext('/process-jobs/retry', new RetryHandler())
			server.createContext('/process-jobs/status', new StatusHandler())
			server.createContext('/process-jobs/health', new HealthHandler())

			server.start()
			println "OmegaProcessJobRestServer started on 127.0.0.1:${PORT}"
		} catch (e) {
			println "Failed to start OmegaProcessJobRestServer: ${e.message}"
		}
	}

	void stop() {
		if (server) {
			server.stop(1)
			if (server.executor) {
				(server.executor as java.util.concurrent.ExecutorService)?.shutdownNow()
			}
			println "OmegaProcessJobRestServer stopped"
		}
	}

	// --- Handlers ---

	/**
	 * POST /process-jobs/execute
	 * Directly invokes OmegaProcessJobProvider.execute() with given config.
	 * Body: { "simulateFailure": "true", "sleepSeconds": 2, "outputMessage": "...", "succeedOnAttempt": 0 }
	 */
	private class ExecuteHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'POST') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def body = parseBody(exchange)
				def request = new ProcessJobExecutionRequest()
				request.processEventId = body.processEventId as Long ?: 0L
				request.opts = body

				ServiceResponse result = processJobProvider.execute(request)
				sendJson(exchange, 200, [
					success : result.success,
					msg     : result.msg,
					data    : result.data ? [nextOpts: result.data.nextOpts] : null
				])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * POST /process-jobs/create
	 * Creates a Process with a single omega process job step.
	 * Body: { "workloadId": 123, "config": { "simulateFailure": "true", ... }, "eventTitle": "..." }
	 * Returns: { "success": true, "processId": 123, "eventId": 456 }
	 */
	private class CreateHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'POST') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def body = parseBody(exchange)
				def config = (body.config as Map) ?: [sleepSeconds: 2, outputMessage: 'Omega test step']
				def eventTitle = body.eventTitle ?: 'Omega Process Job Test Step'
				Long workloadId = body.workloadId as Long

				if (!workloadId) {
					sendJson(exchange, 400, [success: false, msg: 'workloadId is required'])
					return
				}

				Workload workload = morpheusContext.services.workload.get(workloadId)
				if (!workload) {
					sendJson(exchange, 404, [success: false, msg: "Workload ${workloadId} not found"])
					return
				}

				// Start a process via the proper API
				def savedProcess = morpheusContext.services.process.startProcess(
					workload, ProcessStepType.forCode('general'), null, 'omega-test', eventTitle
				)

				if (!savedProcess?.id) {
					sendJson(exchange, 500, [success: false, msg: 'Failed to create process'])
					return
				}

				// Insert a step
				def stepEvent = new ProcessEvent()
				stepEvent.stepType = ProcessStepType.forCode('general')
				stepEvent.eventTitle = eventTitle
				stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
				stepEvent.jobConfig = config

				def insertRequest = new InsertProcessStepRequest(savedProcess, stepEvent)
				InsertProcessStepResponse insertResponse = morpheusContext.services.process.insertProcessStep(insertRequest)

				sendJson(exchange, 200, [
					success  : true,
					processId: savedProcess.id,
					eventId  : insertResponse?.processEventId
				])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * POST /process-jobs/create-multi-step
	 * Creates a Process with multiple omega process job steps.
	 * Body: { "workloadId": 123, "stepCount": 3, "stepConfigs": [ {...}, {...}, {...} ] }
	 * Returns: { "success": true, "processId": 123, "steps": [ { "eventId": 456, "runOrder": 1 }, ... ] }
	 */
	private class CreateMultiStepHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'POST') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def body = parseBody(exchange)
				Integer stepCount = (body.stepCount as Integer) ?: 3
				List stepConfigs = (body.stepConfigs as List) ?: []
				Long workloadId = body.workloadId as Long

				if (!workloadId) {
					sendJson(exchange, 400, [success: false, msg: 'workloadId is required'])
					return
				}

				Workload workload = morpheusContext.services.workload.get(workloadId)
				if (!workload) {
					sendJson(exchange, 404, [success: false, msg: "Workload ${workloadId} not found"])
					return
				}

				// Start a process via the proper API
				def savedProcess = morpheusContext.services.process.startProcess(
					workload, ProcessStepType.forCode('general'), null, 'omega-test-multi'
				)

				if (!savedProcess?.id) {
					sendJson(exchange, 500, [success: false, msg: 'Failed to create process'])
					return
				}

				// Insert steps
				def steps = []
				for (int i = 0; i < stepCount; i++) {
					def config = (i < stepConfigs.size() ? stepConfigs[i] : null) as Map
					config = config ?: [sleepSeconds: 2, outputMessage: "Step ${i + 1} complete"]

					def stepEvent = new ProcessEvent()
					stepEvent.stepType = ProcessStepType.forCode('general')
					stepEvent.eventTitle = "Omega Step ${i + 1}"
					stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
					stepEvent.jobConfig = config

					def insertRequest = new InsertProcessStepRequest(savedProcess, stepEvent)
					InsertProcessStepResponse insertResponse = morpheusContext.services.process.insertProcessStep(insertRequest)

					steps << [eventId: insertResponse?.processEventId, runOrder: i + 1]
				}

				sendJson(exchange, 200, [
					success  : true,
					processId: savedProcess.id,
					steps    : steps
				])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * POST /process-jobs/run
	 * Dispatches a process step for execution via the normal pipeline.
	 * Body: { "processId": 123, "eventId": 456 }
	 */
	private class RunHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'POST') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def body = parseBody(exchange)
				Long processId = body.processId as Long
				Long eventId = body.eventId as Long

				if (!processId || !eventId) {
					sendJson(exchange, 400, [success: false, msg: 'processId and eventId are required'])
					return
				}

				def processModel = new ProcessModel()
				processModel.id = processId

				def eventModel = new ProcessEvent()
				eventModel.id = eventId

				def request = new RunProcessStepRequest(processModel, eventModel)
				RunProcessStepResponse response = morpheusContext.services.process.runProcessStep(request)

				sendJson(exchange, 200, [success: response?.success ?: false])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * POST /process-jobs/retry
	 * Retries a failed process step — resets status and dispatches via pipeline.
	 * Body: { "processId": 123, "eventId": 456, "config": { ... } }
	 * The config can override step inputs for retry-with-modified-inputs.
	 */
	private class RetryHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'POST') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def body = parseBody(exchange)
				Long processId = body.processId as Long
				Long eventId = body.eventId as Long

				if (!processId || !eventId) {
					sendJson(exchange, 400, [success: false, msg: 'processId and eventId are required'])
					return
				}

				// Use the existing Morpheus REST API retry endpoint internally
				// Or dispatch directly — retry is the same as run, the platform handles status reset
				def processModel = new ProcessModel()
				processModel.id = processId

				def eventModel = new ProcessEvent()
				eventModel.id = eventId
				// If config overrides are provided, set them as jobConfig
				if (body.config) {
					eventModel.jobConfig = body.config as Map
				}

				def request = new RunProcessStepRequest(processModel, eventModel)
				RunProcessStepResponse response = morpheusContext.services.process.runProcessStep(request)

				sendJson(exchange, 200, [success: response?.success ?: false])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * GET /process-jobs/status?processId=123
	 * Returns process and event status information.
	 */
	private class StatusHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'GET') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def params = parseQueryParams(exchange)
				Long processId = params.processId as Long

				if (!processId) {
					sendJson(exchange, 400, [success: false, msg: 'processId query param is required'])
					return
				}

				def process = morpheusContext.services.process.get(processId)
				if (!process) {
					sendJson(exchange, 404, [success: false, msg: 'Process not found'])
					return
				}

				sendJson(exchange, 200, [
					success  : true,
					processId: process.id,
					process  : [
						id      : process.id,
						stepType: process.stepType?.code
					]
				])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * GET /process-jobs/health
	 * Simple health check endpoint.
	 */
	private class HealthHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			sendJson(exchange, 200, [status: 'ok', provider: OmegaProcessJobProvider.PROVIDER_CODE])
		}
	}

	// --- Utility methods ---

	private Map parseBody(HttpExchange exchange) {
		def bodyText = exchange.requestBody?.text ?: '{}'
		return (jsonSlurper.parseText(bodyText) as Map) ?: [:]
	}

	private Map parseQueryParams(HttpExchange exchange) {
		def query = exchange.requestURI.query ?: ''
		def params = [:]
		query.split('&').each { param ->
			def parts = param.split('=', 2)
			if (parts.length == 2) {
				params[parts[0]] = URLDecoder.decode(parts[1], 'UTF-8')
			}
		}
		return params
	}

	private void sendJson(HttpExchange exchange, int statusCode, Map body) {
		def json = JsonOutput.toJson(body)
		def bytes = json.getBytes('UTF-8')
		exchange.responseHeaders.set('Content-Type', 'application/json')
		exchange.sendResponseHeaders(statusCode, bytes.length)
		exchange.responseBody.write(bytes)
		exchange.responseBody.close()
	}
}
