package com.morpheusdata.omega.deviceCredential

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.CypherModuleProvider
import com.morpheusdata.cypher.CypherModule

/**
 * Registers the device password CypherModule at the "ilo-device" mount point.
 * This mount point is referenced by the {@link DevicePasswordCredentialTypeProvider}'s
 * backendConfig: {"mountPath": "ilo-device"}.
 *
 * When credentials of type "iLO-device-username-password" are saved, the credential service
 * routes the write through MorpheusCypherService to this mount point, where
 * {@link DevicePasswordCypherModule} handles persistence and optional device push.
 */
class DevicePasswordCypherProvider implements CypherModuleProvider {
	MorpheusContext morpheusContext
	Plugin plugin

	DevicePasswordCypherProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	CypherModule getCypherModule() {
		DevicePasswordCypherModule module = new DevicePasswordCypherModule()
		module.setMorpheusContext(this.morpheusContext)
		module.setPlugin(this.plugin)
		return module
	}

	@Override
	String getCypherMountPoint() {
		return 'ilo-device'
	}

	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

	@Override
	String getCode() {
		return 'ilo-device-cypher'
	}

	@Override
	String getName() {
		return 'iLO Device Cypher'
	}
}
