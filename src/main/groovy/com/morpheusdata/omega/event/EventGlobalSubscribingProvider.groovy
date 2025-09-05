package com.morpheusdata.omega.event

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.PluginProvider
import com.morpheusdata.model.AccountIntegration
import com.morpheusdata.model.event.ClusterEvent
import com.morpheusdata.model.event.DatastoreEvent
import com.morpheusdata.model.event.Event
import com.morpheusdata.model.event.EventType
import com.morpheusdata.model.event.GlobalEventType
import com.morpheusdata.model.event.NetworkEvent
import groovy.util.logging.Slf4j

/**
 * An EventSubscriber for all events in Morpheus.
 * <p>
 * This subscriber is independent of any AccountIntegration and receives events from all entities related to the
 * subscribed event types.
 */
@Slf4j
class EventGlobalSubscribingProvider implements PluginProvider, PluginProvider.EventSubscriberFacet{
	private final Plugin plugin
	private final MorpheusContext morpheusContext

	EventGlobalSubscribingProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Plugin getPlugin() {
		return plugin
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getCode() { 'omega.event.all'}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getName() {
		return "Omega Event Subscriber"
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	List<EventType> getSupportedEventTypes() {[
			GlobalEventType.ALL,

			ClusterEvent.ClusterEventType.CREATE,
			ClusterEvent.ClusterEventType.UPDATE,
			ClusterEvent.ClusterEventType.DELETE,
			ClusterEvent.ClusterEventType.ADD_WORKER,
			ClusterEvent.ClusterEventType.REMOVE_WORKER,
			DatastoreEvent.DatastoreEventType.SERVER_MOVE,
			DatastoreEvent.DatastoreEventType.SERVER_SHUTDOWN,
			DatastoreEvent.DatastoreEventType.SERVER_STARTUP,
			DatastoreEvent.DatastoreEventType.VOLUME_ATTACH,
			DatastoreEvent.DatastoreEventType.VOLUME_DETACH,
			DatastoreEvent.DatastoreEventType.VOLUME_MOVE_INITIATED,
			DatastoreEvent.DatastoreEventType.VOLUME_MOVE_COMPLETED,
			DatastoreEvent.DatastoreEventType.VOLUME_MOVE_FAILED,
			NetworkEvent.NetworkEventType.CREATE,
			NetworkEvent.NetworkEventType.UPDATE,
			NetworkEvent.NetworkEventType.DELETE,
	]}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void onEvent(Event event, AccountIntegration integration) {
		// Integration should always be null since this class isn't an account integration
		log.info("Got an event with integration {}", integration)
		if (event instanceof NetworkEvent) {
			NetworkEvent netEvent = (NetworkEvent) event
			log.info("Got a network event - message: {}, type: {}, network: {}",
				netEvent.getMessage(), netEvent.getType(), netEvent.getNetwork()?.name)
		} else if (event instanceof DatastoreEvent) {
			DatastoreEvent dsEvent = (DatastoreEvent) event
			log.info("Got a datastore event - message: {}, type: {}, sourceDatastore: {}, targetDatastore: {}, server: {}, volume: {}, sourceHost: {}, targetHost: {}",
				dsEvent.getMessage(), dsEvent.getType(), dsEvent.getSourceDatastore()?.name, dsEvent.getTargetDatastore()?.name,
				dsEvent.getServer()?.name, dsEvent.getVolume()?.name, dsEvent.getSourceHost()?.name, dsEvent.getTargetHost()?.name)
		} else if (event instanceof ClusterEvent) {
			ClusterEvent clusterEvent = (ClusterEvent) event
			log.info("Got a cluster event - message: {}, type: {}, cluster: {}, server: {}",
				clusterEvent.getMessage(), clusterEvent.getType(), clusterEvent.getCluster()?.name, clusterEvent.getServer()?.name)
		} else {
			log.info("Got an unknown event type - class: {}, message: {}, type: {}",
				event.getClass().getSimpleName(), event.getMessage(), event.getType())
		}
	}
}
