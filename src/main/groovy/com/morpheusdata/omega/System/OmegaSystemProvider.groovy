package com.morpheusdata.omega.system

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.SystemProvider
import com.morpheusdata.model.Icon
import com.morpheusdata.model.system.System
import com.morpheusdata.model.system.SystemComponentType
import com.morpheusdata.model.system.SystemType
import com.morpheusdata.model.system.SystemTypeLayout
import com.morpheusdata.omega.logging.LogWrapper

class OmegaSystemProvider implements SystemProvider {

	public static final String SYSTEM_PROVIDER_CODE = 'omega.system'

	protected MorpheusContext morpheusContext
	protected Plugin plugin
	protected final LogWrapper log = LogWrapper.instance

	OmegaSystemProvider(Plugin plugin, MorpheusContext morpheusContext) {
		super()
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	@Override
	String getDescription() {
		return 'This is a custom system provider for Morpheus Omega Test Plugin.'
	}

	@Override
	Icon getIcon() {
		return new Icon(path: "morpheus.svg", darkPath: "morpheus.svg")
	}

	@Override
	Collection<SystemComponentType> getSystemComponentTypes() {
		return []
	}

	@Override
	Collection<SystemType> getSystemTypes() {
		def systemType = new SystemType()
		systemType.code = 'omega.system'
		systemType.name = 'Omega Test System'
		systemType.description = 'Test system type for Omega plugin'
		systemType.active = true
		return [systemType]
	}

	@Override
	Collection<SystemTypeLayout> getSystemTypeLayouts() {
		def systemType = new SystemType()
		systemType.code = 'omega.system'

		def layout = new SystemTypeLayout()
		layout.code = 'omega.system.layout'
		layout.name = 'Omega System Layout'
		layout.description = 'Default layout for Omega test systems'
		layout.systemType = systemType
		return [layout]
	}

	@Override
	MorpheusContext getMorpheus() {
		return this.@morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return this.@plugin
	}

	@Override
	String getCode() {
		return SYSTEM_PROVIDER_CODE
	}

	@Override
	String getName() {
		return "System Test Provider"
	}
}
