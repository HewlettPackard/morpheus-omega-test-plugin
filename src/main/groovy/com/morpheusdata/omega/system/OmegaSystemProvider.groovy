package com.morpheusdata.omega.system

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.SystemProvider
import com.morpheusdata.model.GenerateSupportBundleContentsRequest
import com.morpheusdata.model.Icon
import com.morpheusdata.model.system.System
import com.morpheusdata.model.system.SystemComponentType
import com.morpheusdata.model.system.SystemType
import com.morpheusdata.model.system.SystemTypeLayout
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.response.ServiceResponse
import groovy.json.JsonOutput

class OmegaSystemProvider implements SystemProvider, SystemProvider.SystemSupportBundleFacet {

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

	/**
	 * Generate support bundle contents for this system.
	 *
	 * @param system The system to generate support bundle contents for
	 * @param request The request containing the target directory and resource info
	 * @return ServiceResponse indicating success or failure
	 */
	@Override
	ServiceResponse generateSupportBundleContents(System system, GenerateSupportBundleContentsRequest request) {
		log.info("Generating support bundle contents for Omega system: ${system.name}")

		try {
			def contentsDir = request.contentsDir

			// Add system configuration details
			def systemConfigFile = contentsDir['omega-system-config.json']
			def systemConfig = [
				systemId       : system.id,
				systemName     : system.name,
				status         : system.status,
				enabled        : system.enabled,
				accountId      : system.owner?.id,
				createdDate    : system.dateCreated?.toString(),
				lastUpdated    : system.lastUpdated?.toString()
			]
			systemConfigFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(systemConfig))

			log.info("Successfully generated support bundle contents for Omega system: ${system.name}")
			return ServiceResponse.success()

		} catch (Exception e) {
			log.error("Error generating support bundle contents for Omega system ${system.name}: ${e.message}", e)
			return ServiceResponse.error("Failed to generate support bundle contents: ${e.message}")
		}
	}
}
