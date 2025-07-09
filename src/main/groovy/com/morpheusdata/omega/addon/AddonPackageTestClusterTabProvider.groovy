package com.morpheusdata.omega.addon

import com.morpheusdata.core.AbstractClusterTabProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Account
import com.morpheusdata.model.ComputeServerGroup
import com.morpheusdata.model.ComputeServerGroupPackage
import com.morpheusdata.model.User
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel

class AddonPackageTestClusterTabProvider extends AbstractClusterTabProvider {
	protected MorpheusContext morpheusContext
	protected Plugin plugin

	AddonPackageTestClusterTabProvider(Plugin plugin, MorpheusContext morpheusContext) {
				this.@morpheusContext = morpheusContext
				this.@plugin = plugin
		}


	/**
	 * {@inheritDoc}
	 */
	@Override
	HTMLResponse renderTemplate(ComputeServerGroup cluster) {
		ViewModel<ComputeServerGroup> model = new ViewModel()
		model.object = cluster
		return getRenderer().renderTemplate("hbs/addonClusterTab", model)
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean show(ComputeServerGroup cluster, User user, Account account) {
		//check for installed package
		def packageMatch = cluster.packages.find {it.packageType.code == AddonPackageTypeProvider.PACKAGE_PROVIDER_CODE}
				return packageMatch?.status == ComputeServerGroupPackage.Status.OK
	}


	/**
	 * {@inheritDoc}
	 */
	MorpheusContext getMorpheus() {
		return this.@morpheusContext
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	Plugin getPlugin() {
		return this.@plugin
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getCode() {
		return 'omega-addon-package-test-plugin-instanceTab'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getName() {
		return 'Test Tab'
	}
}
