package com.morpheusdata.omega.processjob

import com.morpheusdata.core.AbstractInstanceTabProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Account
import com.morpheusdata.model.Instance
import com.morpheusdata.model.User
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel

/**
 * Instance tab that provides a UI for triggering OmegaProcessJobProvider steps.
 * Allows users to configure step options (simulateFailure, succeedOnAttempt, sleepSeconds)
 * and start process jobs directly from an instance detail page.
 *
 * @since 0.4.0
 */
class OmegaProcessJobTabProvider extends AbstractInstanceTabProvider {

	protected Plugin plugin
	protected MorpheusContext morpheusContext

	OmegaProcessJobTabProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext
	}

	@Override
	String getCode() {
		return 'omega-process-job-tab'
	}

	@Override
	String getName() {
		return 'Process Jobs'
	}

	@Override
	HTMLResponse renderTemplate(Instance instance) {
		ViewModel<Map> model = new ViewModel<>()
		// Pass instance data to the template
		def containers = instance.containers?.collect { c ->
			[id: c.id, name: c.name ?: "Container ${c.id}"]
		} ?: []
		model.object = [
			instanceId: instance.id,
			instanceName: instance.name,
			containers: containers
		]
		getRenderer().renderTemplate("hbs/processJobTab", model)
	}

	@Override
	Boolean show(Instance instance, User user, Account account) {
		// Show the tab for all instances — users can trigger process jobs on any workload
		return true
	}
}
