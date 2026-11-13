package com.morpheusdata.omega

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.providers.PluginProvider
import spock.lang.Specification

class MorpheusOmegaTestPluginSpec extends Specification {
	void "all providers retain their plugin context and legacy callbacks without initialization side effects"() {
		given:
		MorpheusContext context = Mock()
		def plugin = new TestPlugin(context)

		when:
		plugin.initialize()

		then:
		plugin.providers.each {
			assert it.plugin.is(plugin): it.code
			assert it.morpheus.is(context): it.code
		}
		plugin.providers.findAll { it instanceof PluginProvider.EventSubscriberFacet }*.code.toSet() ==
			['omega.event.all', 'omega.event.cluster-integration'].toSet()
		plugin.controllers.empty
		0 * context._
	}

	private static class TestPlugin extends MorpheusOmegaTestPlugin {
		TestPlugin(MorpheusContext context) {
			this.morpheus = context
		}
	}
}
