package com.morpheusdata.omega.event

import com.morpheusdata.core.MorpheusAsyncServices
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusServices
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.event.EventConsumerContext
import com.morpheusdata.core.event.MorpheusEventCollectorService
import com.morpheusdata.core.event.MorpheusEventHistoryService
import com.morpheusdata.core.synchronous.event.MorpheusSynchronousEventCollectorService
import com.morpheusdata.core.synchronous.event.MorpheusSynchronousEventHistoryService
import com.morpheusdata.model.Account
import com.morpheusdata.model.User
import com.morpheusdata.model.event.EventEnvelope
import com.morpheusdata.model.event.EventErrorCode
import com.morpheusdata.model.event.EventHistoryPage
import com.morpheusdata.model.event.EventSubscription
import com.morpheusdata.model.event.EventUpdateSet
import com.morpheusdata.request.event.EventAckRequest
import com.morpheusdata.request.event.EventHistoryRequest
import com.morpheusdata.request.event.EventSubscriptionCreateRequest
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.ViewModel
import io.reactivex.rxjava3.core.Single
import jakarta.servlet.http.HttpServletRequest
import spock.lang.Specification

class EventQualificationControllerSpec extends Specification {
	Plugin plugin = Stub()
	MorpheusEventCollectorService collector = Mock()
	MorpheusEventHistoryService history = Mock()
	MorpheusContext morpheus = Stub()
	EventQualificationController controller

	def setup() {
		def blockingCollector = new BlockingCollector(eventCollectorService: collector)
		def blockingHistory = new BlockingHistory(eventHistoryService: history)
		morpheus.getAsync() >> Stub(MorpheusAsyncServices) {
			getEventCollector() >> collector
			getEventHistory() >> history
		}
		morpheus.getServices() >> Stub(MorpheusServices) {
			getEventCollector() >> blockingCollector
			getEventHistory() >> blockingHistory
		}
		controller = new EventQualificationController(plugin, morpheus)
	}

	void "both SDK aggregates use the loaded plugin and authenticated tenant for create and deletion"() {
		when:
		def created = controller.execute(model([
			api: api, operation: 'create', integrationId: 42,
			request: [name: 'qualification', eventTypes: ['vm.*']]
		]))
		def deleted = controller.execute(model([api: api, operation: 'delete', id: 'subscription']))

		then:
		1 * collector.create({ EventConsumerContext context ->
			context.plugin.is(plugin) && context.accountId == 17L && context.integrationId == 42L
		}) { EventSubscriptionCreateRequest request -> request.name == 'qualification' && request.eventTypes == ['vm.*'] } >>
			Single.just(ServiceResponse.success(new EventSubscription(id: 'subscription', version: 'accepted')))
		1 * collector.delete({ it.accountId == 17L && it.integrationId == null }, 'subscription') >>
			Single.just(new ServiceResponse<Void>(true, null, null, null))
		created.status == 200
		created.data.data.id == 'subscription'
		created.data.data.version == 'accepted'
		deleted.status == 200
		deleted.data.success
		deleted.data.data == null

		where:
		api << ['sync', 'async']
	}

	void "stale ACK recovery survives HTTP serialization on both API styles"() {
		when:
		def result = controller.execute(model([api: api, operation: 'ack', id: 'subscription', request: [version: 'old']]))

		then:
		1 * collector.ack(_, 'subscription') { EventAckRequest request -> request.version == 'old' } >>
			Single.just(EventErrorCode.staleVersion('Stale batch', 'accepted'))
		result.status == 409
		!result.data.success
		result.data.errorCode == 'STALE_VERSION'
		result.data.statusCode == '409'
		result.data.errors.lastAcceptedVersion == 'accepted'
		result.data.data == null

		where:
		api << ['sync', 'async']
	}

	void "history is independent and DTO fields including unknown codes remain visible"() {
		given:
		def event = new EventEnvelope(uuid: UUID.randomUUID().toString(), eventType: 'vm.futureAction',
			eventDate: new Date(1700000000000L), subjectRevision: 3L, details: [future: true])

		when:
		def result = controller.execute(model([
			api: api, operation: 'history', request: [subjectKey: 'vm:identity', maxResults: 2]
		]))

		then:
		1 * history.query(_) { EventHistoryRequest request -> request.subjectKey == 'vm:identity' && request.maxResults == 2 } >>
			Single.just(ServiceResponse.success(new EventHistoryPage(events: [event], nextCursor: 'next')))
		0 * collector._
		result.status == 200
		result.data.data.events.first().eventType == 'vm.futureAction'
		result.data.data.events.first().subjectRevision == 3L
		result.data.data.events.first().details.future
		result.data.data.events.first().eventDate instanceof String
		result.data.data.nextCursor == 'next'

		where:
		api << ['sync', 'async']
	}

	void "read returns the actual update set and get and list preserve backend failures"() {
		when:
		def read = controller.execute(model([operation: 'read', id: 'subscription', request: [sinceVersion: 'accepted']]))
		def get = controller.execute(model([operation: 'get', id: 'missing']))
		def list = controller.execute(model([operation: 'list']))

		then:
		1 * collector.read(_, 'subscription') { it.sinceVersion == 'accepted' } >>
			Single.just(ServiceResponse.success(new EventUpdateSet(version: 'accepted', events: [], observedLossCount: 4L)))
		1 * collector.get(_, 'missing') >> Single.just(EventErrorCode.SUBSCRIPTION_NOT_FOUND.failure('Missing'))
		1 * collector.list(_, null) >> Single.just(EventErrorCode.DEPENDENCY_UNAVAILABLE.failure('Offline'))
		read.data.data.observedLossCount == 4L
		read.data.data.events == []
		get.status == 404
		list.status == 503
		!get.data.success
		!list.data.success
	}

	void "invalid transport input cannot invoke a trusted SDK operation"() {
		given:
		def request = model(body)
		request.request = Stub(HttpServletRequest) { getMethod() >> method }

		when:
		def result = controller.execute(request)

		then:
		result.status == status
		!result.data.success
		0 * collector._
		0 * history._

		where:
		body                                                   | method | status
		[operation: 'delete', id: 'subscription']                | 'GET'  | 405
		[operation: 'create', accountId: 18]                     | 'POST' | 400
		[operation: 'read', request: [maxEvents: 1.5]]            | 'POST' | 400
		[operation: 'get', integrationId: 1.5]                   | 'POST' | 400
		[operation: 'publish']                                  | 'POST' | 400
		[operation: 'get', api: 'other']                         | 'POST' | 400
	}

	void "a missing authenticated tenant cannot invoke the trusted SDK"() {
		given:
		def input = model([operation: 'create'])
		input.user = null

		when:
		def result = controller.execute(input)

		then:
		result.status == 403
		0 * collector._
		0 * history._
	}

	private ViewModel<Map> model(Map input) {
		new ViewModel<Map>(object: input, user: new User(account: new Account(id: 17L)),
			request: Stub(HttpServletRequest) { getMethod() >> 'POST' })
	}

	private static class BlockingCollector implements MorpheusSynchronousEventCollectorService {
		MorpheusEventCollectorService eventCollectorService
	}

	private static class BlockingHistory implements MorpheusSynchronousEventHistoryService {
		MorpheusEventHistoryService eventHistoryService
	}
}
