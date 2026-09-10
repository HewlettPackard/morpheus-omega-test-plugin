package com.morpheusdata.omega.computehost

import com.morpheusdata.core.AbstractServerTabProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Account
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.User
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel

class OmegaGpuInventoryTabProvider extends AbstractServerTabProvider {

	protected Plugin plugin
	protected MorpheusContext morpheusContext

	OmegaGpuInventoryTabProvider(Plugin plugin, MorpheusContext morpheusContext) {
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
		return 'omega-gpu-inventory-tab'
	}

	@Override
	String getName() {
		return 'GPU Inventory'
	}

	@Override
	HTMLResponse renderTemplate(ComputeServer server) {
		ViewModel<Map> model = new ViewModel<>()
		model.object = [
			serverId  : server.id,
			serverName: server.name,
			version   : this.plugin.version,
			cacheBust : java.lang.System.currentTimeMillis()
		]
		getRenderer().renderTemplate('hbs/gpuInventoryTab', model)
	}

	@Override
	Boolean show(ComputeServer server, User user, Account account) {
		return true
	}
}
