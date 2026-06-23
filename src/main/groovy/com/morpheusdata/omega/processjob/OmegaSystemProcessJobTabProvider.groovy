package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.AbstractSystemTabProvider
import com.morpheusdata.model.Account
import com.morpheusdata.model.User
import com.morpheusdata.model.system.System
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel

/**
 * System tab that provides a UI for triggering OmegaProcessJobProvider steps against a System.
 *
 * This is the System-scoped relocation of the former instance Process Jobs tab. Unlike the instance
 * variant, it starts a process directly on the System (refType='system', no workload) via the
 * MorpheusProcessService#startProcess(System, ...) overload and the
 * /plugin/process-jobs/start-system-process endpoint.
 *
 * The System detail page is React-driven and injects this tab's HTML via innerHTML, so the
 * interactive logic is shipped as an external asset bundle that conforms to the React
 * window.Morpheus.pluginTabs mount contract (see hbs/systemProcessJobTab.hbs).
 *
 * @since 0.4.0
 */
class OmegaSystemProcessJobTabProvider extends AbstractSystemTabProvider {

	protected Plugin plugin
	protected MorpheusContext morpheusContext

	OmegaSystemProcessJobTabProvider(Plugin plugin, MorpheusContext morpheusContext) {
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
		return 'omega-system-process-job-tab'
	}

	@Override
	String getName() {
		return 'Process Jobs'
	}

	@Override
	Integer getOrder() {
		return 100
	}

	@Override
	HTMLResponse renderTemplate(System system) {
		ViewModel<Map> model = new ViewModel<>()
		model.object = [
			systemId  : system.id,
			systemName: system.name,
			version   : this.plugin.version,
			cacheBust : java.lang.System.currentTimeMillis()
		]
		getRenderer().renderTemplate("hbs/systemProcessJobTab", model)
	}

	@Override
	Boolean show(System system, User user, Account account) {
		// The backing /plugin/process-jobs/* endpoints require admin-appliance:full, and the
		// /api/processes status lookup for system-scoped processes is master-tenant only. Restrict
		// the tab to the master tenant so non-master users are not shown a non-functional tab.
		return account?.masterAccount == true
	}
}
