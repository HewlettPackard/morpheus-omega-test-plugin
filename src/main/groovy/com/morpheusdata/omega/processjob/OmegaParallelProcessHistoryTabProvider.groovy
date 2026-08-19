package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.AbstractSystemTabProvider
import com.morpheusdata.model.Account
import com.morpheusdata.model.User
import com.morpheusdata.model.system.System
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel

class OmegaParallelProcessHistoryTabProvider extends AbstractSystemTabProvider {

	protected Plugin plugin
	protected MorpheusContext morpheusContext

	OmegaParallelProcessHistoryTabProvider(Plugin plugin, MorpheusContext morpheusContext) {
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
		return 'omega-parallel-process-history-tab'
	}

	@Override
	String getName() {
		return 'Parallel Process History'
	}

	@Override
	Integer getOrder() {
		return 101
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
		getRenderer().renderTemplate('hbs/parallelProcessHistoryTab', model)
	}

	@Override
	Boolean show(System system, User user, Account account) {
		return account?.masterAccount == true
	}
}
