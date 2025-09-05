package com.morpheusdata.omega.event

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.AbstractGenericIntegrationProvider
import com.morpheusdata.core.providers.PluginProvider
import com.morpheusdata.model.AccountIntegration
import com.morpheusdata.model.AccountIntegrationType
import com.morpheusdata.model.Icon
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.event.ClusterEvent
import com.morpheusdata.model.event.Event
import com.morpheusdata.model.event.EventType
import com.morpheusdata.views.HTMLResponse
import groovy.util.logging.Slf4j

/**
 * This is purely for testing. If you don't have a need for a UI representation for the provider, you should instead follow
 * {@link EventGlobalSubscribingProvider}, implement {@link PluginProvider.EventSubscriberFacet} and do a filter in `onEvent()`
 * for the events you're interested in.
 */
@Slf4j
class EventClusterSubscribingIntegration extends AbstractGenericIntegrationProvider implements PluginProvider.EventSubscriberFacet {
	private Plugin plugin
	private MorpheusContext morpheusContext

	EventClusterSubscribingIntegration(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getCategory() {
		return AccountIntegration.Category.other
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	List<OptionType> getOptionTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	void refresh(AccountIntegration accountIntegration) {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getIcon() {
		return new Icon(path: 'omega.svg', darkPath: 'omega-dark.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	HTMLResponse renderTemplate(AccountIntegration integration) {
		return new HTMLResponse()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.context
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getCode() {
		return 'omega.event.cluster-integration'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getName() {
		return 'Omega Cluster Event Integration'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	AccountIntegrationType.AssociationType getClusterAssociationType() {
		return AccountIntegrationType.AssociationType.MANY
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	List<EventType> getSupportedEventTypes() { [
			ClusterEvent.ClusterEventType.ADD_WORKER,
			ClusterEvent.ClusterEventType.REMOVE_WORKER,
			ClusterEvent.ClusterEventType.CREATE,
			ClusterEvent.ClusterEventType.UPDATE,
			ClusterEvent.ClusterEventType.DELETE,
	]}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void onEvent(Event event, AccountIntegration integration) {
		if (event instanceof ClusterEvent) {
			ClusterEvent clusterEvent = (ClusterEvent) event
			log.info("Got a cluster event - message: {}, type: {}, cluster: {}, server: {}",
					clusterEvent.getMessage(), clusterEvent.getType(), clusterEvent.getCluster()?.name, clusterEvent.getServer()?.name)
		} else {
			log.error("Got an unknown event type - class: {}, message: {}, type: {}",
					event.getClass().getSimpleName(), event.getMessage(), event.getType())
		}
	}
}
