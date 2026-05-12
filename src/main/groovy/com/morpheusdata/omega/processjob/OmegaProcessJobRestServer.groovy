package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
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
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.concurrent.Executors

/**
 * Lightweight HTTP server embedded in the omega test plugin to exercise
 * OmegaProcessJobProvider workflows via REST calls. Binds to 127.0.0.1:8090.
 *
 * Endpoints:
 *   POST /process-jobs/execute           — directly invoke OmegaProcessJobProvider.execute()
 *   POST /process-jobs/create            — create a process with a single omega job step
 *   POST /process-jobs/create-multi-step — create a process with multiple omega job steps
 *   POST /process-jobs/start-process     — start a standalone process on an existing workload
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
			server.createContext('/process-jobs/start-process', new StartProcessHandler())
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
	 * POST /process-jobs/start-process
	 * Starts a standalone process on a pre-existing workload via
	 * MorpheusProcessService#startProcess(), then inserts one or more omega
	 * process job steps so the process is immediately usable.
	 *
	 * Body: {
	 *   "workloadId": 123,                         // required
	 *   "userId": 1,                               // optional, defaults to 1 (admin)
	 *   "description": "...",                       // optional, process description
	 *   "stepTypeCode": "general",                  // optional, defaults to "general"
	 *   "stepCount": 2,                             // optional, defaults to 1
	 *   "stepConfigs": [                            // optional, per-step overrides
	 *     { "sleepSeconds": 3, "outputMessage": "Step 1 done" },
	 *     { "sleepSeconds": 1, "outputMessage": "Step 2 done" }
	 *   ]
	 * }
	 *
	 * Returns: { "success": true, "processId": 456, "steps": [...] }
	 */
	private class StartProcessHandler implements HttpHandler {
		@Override
		void handle(HttpExchange exchange) {
			if (exchange.requestMethod != 'POST') {
				sendJson(exchange, 405, [success: false, msg: 'Method not allowed'])
				return
			}
			try {
				def body = parseBody(exchange)
				Long workloadId = body.workloadId as Long
				Long userId = (body.userId as Long) ?: 1L
				String description = (body.description as String) ?: 'Omega dummy process'
				String stepTypeCode = (body.stepTypeCode as String) ?: 'general'
				Integer stepCount = (body.stepCount as Integer) ?: 1
				List stepConfigs = (body.stepConfigs as List) ?: []

				if (!workloadId) {
					sendJson(exchange, 400, [success: false, msg: 'workloadId is required'])
					return
				}

				def workload = morpheusContext.services.workload.get(workloadId)
				if (!workload) {
					sendJson(exchange, 404, [success: false, msg: "Workload ${workloadId} not found"])
					return
				}

				User user = morpheusContext.services.admin.user.get(userId)
				if (!user) {
					sendJson(exchange, 404, [success: false, msg: "User ${userId} not found"])
					return
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

				sendJson(exchange, 200, [
					success  : process != null,
					processId: process?.id,
					steps    : steps,
					msg      : "Process started for workload ${workloadId} with ${stepCount} step(s)"
				])
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * POST /process-jobs/create
	 * Creates an Omega system via the Morpheus API, which bootstraps a Process with
	 * omega process job steps through OmegaSystemProvider's lifecycle hooks.
	 *
	 * Headers: Authorization: Bearer {morpheus-api-token}
	 * Body: { "morpheusUrl": "https://localhost", "name": "test-system-1", "config": {...} }
	 *
	 * The morpheusUrl defaults to https://localhost if not provided.
	 * Returns: { "success": true, "systemId": 123 }
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
				def authToken = extractBearerToken(exchange)

				if (!authToken) {
					sendJson(exchange, 401, [success: false, msg: 'Authorization: Bearer <token> header required'])
					return
				}

				def morpheusUrl = (body.morpheusUrl as String) ?: 'https://localhost'
				def systemName = (body.name as String) ?: "omega-test-${System.currentTimeMillis()}"

				// Look up the omega system type and layout IDs
				def typeAndLayout = resolveOmegaSystemTypeIds(morpheusUrl, authToken)
				if (!typeAndLayout.success) {
					sendJson(exchange, 500, [success: false, msg: typeAndLayout.msg])
					return
				}

				// Create the system via Morpheus API — this triggers OmegaSystemProvider
				// lifecycle hooks which insert process steps
				def systemPayload = [
					system: [
						name  : systemName,
						type  : [id: typeAndLayout.typeId],
						layout: [id: typeAndLayout.layoutId]
					]
				]
				if (body.config) {
					systemPayload.system.config = body.config
				}

				def result = morpheusApiRequest(morpheusUrl, '/api/v1/systems', 'POST', authToken, systemPayload)

				if (result.success) {
					sendJson(exchange, 200, [
						success : true,
						systemId: result.data?.id,
						msg     : 'System created — OmegaSystemProvider lifecycle will bootstrap process steps'
					])
				} else {
					sendJson(exchange, result.statusCode ?: 500, [
						success: false,
						msg    : result.msg ?: 'Failed to create system via Morpheus API',
						errors : result.data?.errors
					])
				}
			} catch (e) {
				sendJson(exchange, 500, [success: false, msg: e.message])
			}
		}
	}

	/**
	 * POST /process-jobs/create-multi-step
	 * Creates an Omega system (bootstrapping a process), then inserts additional
	 * omega process job steps into that process.
	 *
	 * Headers: Authorization: Bearer {morpheus-api-token}
	 * Body: { "morpheusUrl": "https://localhost", "name": "multi-step-test",
	 *         "extraStepCount": 2, "stepConfigs": [{...}, {...}] }
	 *
	 * The system creation itself adds steps via OmegaSystemProvider. The extraStepCount
	 * adds additional steps beyond what the provider inserts.
	 * Returns: { "success": true, "systemId": 123, "extraSteps": [...] }
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
				def authToken = extractBearerToken(exchange)

				if (!authToken) {
					sendJson(exchange, 401, [success: false, msg: 'Authorization: Bearer <token> header required'])
					return
				}

				def morpheusUrl = (body.morpheusUrl as String) ?: 'https://localhost'
				def systemName = (body.name as String) ?: "omega-multi-${System.currentTimeMillis()}"
				Integer extraStepCount = (body.extraStepCount as Integer) ?: 2
				List stepConfigs = (body.stepConfigs as List) ?: []

				// Create system first (bootstraps process with provider steps)
				def typeAndLayout = resolveOmegaSystemTypeIds(morpheusUrl, authToken)
				if (!typeAndLayout.success) {
					sendJson(exchange, 500, [success: false, msg: typeAndLayout.msg])
					return
				}

				def systemPayload = [
					system: [
						name  : systemName,
						type  : [id: typeAndLayout.typeId],
						layout: [id: typeAndLayout.layoutId]
					]
				]

				def createResult = morpheusApiRequest(morpheusUrl, '/api/v1/systems', 'POST', authToken, systemPayload)
				if (!createResult.success) {
					sendJson(exchange, createResult.statusCode ?: 500, [
						success: false,
						msg    : createResult.msg ?: 'Failed to create system'
					])
					return
				}

				Long systemId = createResult.data?.id as Long

				// Find the process created for this system
				def processResult = findSystemProcess(morpheusUrl, authToken, systemId)
				Long processId = processResult?.processId

				// Insert extra steps if a process was found
				def extraSteps = []
				if (processId && extraStepCount > 0) {
					def processModel = new ProcessModel()
					processModel.id = processId

					for (int i = 0; i < extraStepCount; i++) {
						def config = (i < stepConfigs.size() ? stepConfigs[i] : null) as Map
						config = config ?: [sleepSeconds: 2, outputMessage: "Extra step ${i + 1} complete"]

						def stepEvent = new ProcessEvent()
						stepEvent.stepType = ProcessStepType.forCode('general')
						stepEvent.eventTitle = "Omega Extra Step ${i + 1}"
						stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
						stepEvent.jobConfig = config

						def insertRequest = new InsertProcessStepRequest(processModel, stepEvent)
						InsertProcessStepResponse insertResponse = morpheusContext.services.process.insertProcessStep(insertRequest)
						extraSteps << [eventId: insertResponse?.processEventId, runOrder: i + 1]
					}
				}

				sendJson(exchange, 200, [
					success   : true,
					systemId  : systemId,
					processId : processId,
					extraSteps: extraSteps,
					msg       : "System created with ${extraStepCount} extra steps added"
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

	// --- Morpheus API helpers ---

	private String extractBearerToken(HttpExchange exchange) {
		def authHeader = exchange.requestHeaders.getFirst('Authorization') ?: ''
		if (authHeader.startsWith('Bearer ')) {
			return authHeader.substring(7).trim()
		}
		return null
	}

	/**
	 * Look up the omega system type and layout IDs via the Morpheus API.
	 */
	private Map resolveOmegaSystemTypeIds(String morpheusUrl, String token) {
		// Get system types and find omega.system
		def typesResult = morpheusApiRequest(morpheusUrl, '/api/v1/system-types?max=200', 'GET', token)
		if (!typesResult.success) {
			return [success: false, msg: "Failed to fetch system types: ${typesResult.msg}"]
		}

		def omegaType = (typesResult.data?.systemTypes as List)?.find { it.code == 'omega.system' }
		if (!omegaType) {
			return [success: false, msg: 'omega.system type not found — is the plugin loaded?']
		}

		// Find the layout for this type
		def layoutsResult = morpheusApiRequest(morpheusUrl, "/api/v1/system-types/${omegaType.id}/layouts?max=100", 'GET', token)
		if (!layoutsResult.success) {
			return [success: false, msg: "Failed to fetch layouts: ${layoutsResult.msg}"]
		}

		def omegaLayout = (layoutsResult.data?.layouts as List)?.find { it.code == 'omega.system.layout' }
		if (!omegaLayout) {
			return [success: false, msg: 'omega.system.layout not found']
		}

		return [success: true, typeId: omegaType.id, layoutId: omegaLayout.id]
	}

	/**
	 * Find the most recent process for a given system.
	 */
	private Map findSystemProcess(String morpheusUrl, String token, Long systemId) {
		def result = morpheusApiRequest(morpheusUrl, "/api/v1/processes?systemId=${systemId}&max=1&sort=id&order=desc", 'GET', token)
		if (result.success && result.data?.processes) {
			def process = (result.data.processes as List)?.first()
			return [processId: process?.id as Long]
		}
		return [processId: null]
	}

	/**
	 * Makes an HTTP request to the Morpheus API. Trusts all SSL certs (localhost dev).
	 */
	private Map morpheusApiRequest(String baseUrl, String path, String method, String token, Map body = null) {
		try {
			def url = new URL("${baseUrl}${path}")
			def conn = url.openConnection()

			// Trust all certs for localhost development
			if (conn instanceof HttpsURLConnection) {
				def trustAll = [
					checkClientTrusted: { X509Certificate[] certs, String authType -> },
					checkServerTrusted: { X509Certificate[] certs, String authType -> },
					getAcceptedIssuers: { null }
				] as X509TrustManager
				def sc = SSLContext.getInstance('TLS')
				sc.init(null, [trustAll] as TrustManager[], null)
				((HttpsURLConnection) conn).SSLSocketFactory = sc.socketFactory
				((HttpsURLConnection) conn).hostnameVerifier = { hostname, session -> true }
			}

			conn.requestMethod = method
			conn.setRequestProperty('Authorization', "Bearer ${token}")
			conn.setRequestProperty('Content-Type', 'application/json')
			conn.connectTimeout = 30000
			conn.readTimeout = 30000

			if (body && method == 'POST') {
				conn.doOutput = true
				conn.outputStream.write(JsonOutput.toJson(body).getBytes('UTF-8'))
				conn.outputStream.flush()
			}

			int responseCode = conn.responseCode
			def responseStream = (responseCode >= 200 && responseCode < 400) ? conn.inputStream : conn.errorStream
			def responseText = responseStream?.text ?: '{}'
			def responseData = new JsonSlurper().parseText(responseText) as Map

			return [
				success   : responseCode >= 200 && responseCode < 400,
				statusCode: responseCode,
				data      : responseData,
				msg       : responseData?.msg ?: responseData?.message
			]
		} catch (e) {
			return [success: false, msg: "API request failed: ${e.message}", statusCode: 500]
		}
	}
}
