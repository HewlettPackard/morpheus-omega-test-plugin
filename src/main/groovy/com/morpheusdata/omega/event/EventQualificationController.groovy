package com.morpheusdata.omega.event

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.event.EventConsumerContext
import com.morpheusdata.model.Permission
import com.morpheusdata.model.event.EventErrorCode
import com.morpheusdata.request.event.EventAckRequest
import com.morpheusdata.request.event.EventHistoryRequest
import com.morpheusdata.request.event.EventReadRequest
import com.morpheusdata.request.event.EventSubscriptionCreateRequest
import com.morpheusdata.request.event.EventSubscriptionListRequest
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route

/**
 * Explicit, administrator-invoked SDK exercises. Installing Omega does not create
 * subscriptions, publish events, enable recording or modify inventory.
 */
class EventQualificationController implements PluginController {
	private static final ObjectMapper MAPPER = new ObjectMapper()
		.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
	private static final Set<String> INPUT_FIELDS = ['operation', 'api', 'integrationId', 'id', 'request'].toSet()
	private final Plugin plugin
	private final MorpheusContext morpheusContext

	EventQualificationController(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	Plugin getPlugin() { plugin }

	@Override
	MorpheusContext getMorpheus() { morpheusContext }

	@Override
	String getCode() { 'omega.event.qualification' }

	@Override
	String getName() { 'Omega Event SDK Qualification' }

	@Override
	List<Route> getRoutes() {
		[Route.build('/omega/events/qualification', 'execute', Permission.build('admin-plugins', 'full'))]
	}

	JsonResponse<Map> execute(ViewModel<Map> model) {
		if(model.request?.method != 'POST')
			return new JsonResponse<Map>(status: 405, data: [
				success: false, errorCode: 'METHOD_NOT_ALLOWED', statusCode: '405', msg: 'Use POST for SDK qualification'
			])
		if(model.user?.account?.id == null)
			return response(EventErrorCode.FORBIDDEN.failure('An authenticated tenant is required'))
		if(!(model.object instanceof Map) || !INPUT_FIELDS.containsAll(model.object.keySet()))
			return response(EventErrorCode.INVALID_REQUEST.failure('Invalid qualification request fields'))

		ServiceResponse result
		try {
			Map input = model.object
			String api = input.api == null ? 'sync' : MAPPER.convertValue(input.api, String)
			if(!(api in ['sync', 'async']))
				return response(EventErrorCode.INVALID_REQUEST.failure('API must be sync or async'))
			def consumer = new EventConsumerContext(plugin, model.user.account.id,
				MAPPER.convertValue(input.integrationId, Long))
			String id = MAPPER.convertValue(input.id, String)
			boolean asynchronous = api == 'async'
			def services = asynchronous ? morpheusContext.async : morpheusContext.services
			def operationResult
			switch(input.operation) {
				case 'create':
					operationResult = services.eventCollector.create(consumer,
						MAPPER.convertValue(input.request, EventSubscriptionCreateRequest))
					break
				case 'get':
					operationResult = services.eventCollector.get(consumer, id)
					break
				case 'list':
					operationResult = services.eventCollector.list(consumer,
						MAPPER.convertValue(input.request, EventSubscriptionListRequest))
					break
				case 'read':
					operationResult = services.eventCollector.read(consumer, id,
						MAPPER.convertValue(input.request, EventReadRequest))
					break
				case 'ack':
					operationResult = services.eventCollector.ack(consumer, id,
						MAPPER.convertValue(input.request, EventAckRequest))
					break
				case 'delete':
					operationResult = services.eventCollector.delete(consumer, id)
					break
				case 'history':
					operationResult = services.eventHistory.query(consumer,
						MAPPER.convertValue(input.request, EventHistoryRequest))
					break
				default:
					return response(EventErrorCode.INVALID_REQUEST.failure('Unknown qualification operation'))
			}
			result = asynchronous ? operationResult.blockingGet() : operationResult
		} catch(IllegalArgumentException invalid) {
			return response(EventErrorCode.INVALID_REQUEST.failure('Invalid qualification request'))
		}
		response(result)
	}

	private static JsonResponse<Map> response(ServiceResponse result) {
		int status = result.statusCode == null ? (result.success ? 200 : 500) : Integer.parseInt(result.statusCode)
		new JsonResponse<Map>(status: status, data: [
			success: result.success, msg: result.msg, errorCode: result.errorCode,
			statusCode: status.toString(), errors: result.errors,
			data: MAPPER.convertValue(result.data, Object)
		])
	}
}
