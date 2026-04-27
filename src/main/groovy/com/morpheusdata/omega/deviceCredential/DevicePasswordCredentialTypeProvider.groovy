package com.morpheusdata.omega.deviceCredential

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.CredentialTypeProvider
import com.morpheusdata.model.AccountCredentialType
import com.morpheusdata.model.OptionType
import groovy.util.logging.Slf4j

/**
 * Registers a custom credential type for device password management.
 * When credentials of this type are saved, persistence is routed through
 * the Cypher system to the mount path specified in backendConfig,
 * where the {@link DevicePasswordCypherModule} handles the write
 * (including optional device push).
 */
@Slf4j
class DevicePasswordCredentialTypeProvider implements CredentialTypeProvider {
	MorpheusContext morpheusContext
	Plugin plugin

	DevicePasswordCredentialTypeProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	AccountCredentialType getCredentialType() {
		AccountCredentialType credentialType = new AccountCredentialType()
		credentialType.code = 'iLO-device-username-password'
		credentialType.name = 'iLO Username and Password'
		credentialType.nameCode = 'iLO Username and Password'
		credentialType.description = 'Credential type for managing iLO device username and password with optional push-to-device support'
		credentialType.enabled = true
		credentialType.creatable = true
		credentialType.editable = true
		credentialType.backend = 'cypher'
		credentialType.backendConfig = '{"mountPath": "ilo-device"}'
		return credentialType
	}

	@Override
	List<OptionType> getCredentialOptionTypes() {
		return [
			new OptionType(
				name: 'Username',
				code: 'iLO-device-username-password.username',
				fieldName: 'username',
				fieldLabel: 'iLO Server Username',
				fieldContext: 'domain',
				inputType: OptionType.InputType.TEXT,
				required: true,
				displayOrder: 0,
			),
			new OptionType(
				name: 'Password',
				code: 'iLO-device-username-password.password',
				fieldName: 'password',
				fieldLabel: 'iLO Server Password',
				fieldContext: 'domain',
				inputType: OptionType.InputType.PASSWORD,
				required: true,
				displayOrder: 1,
			),
			new OptionType(
				name: 'Push to Device',
				code: 'iLO-device-username-password.pushToDevice',
				fieldName: 'pushToDevice',
				fieldLabel: 'Push to Device',
				inputType: OptionType.InputType.CHECKBOX,
				required: false,
				displayOrder: 3,
				defaultValue: 'on',
				showOnCreate: false,
				helpText: 'When enabled (in-band), the password change is pushed to the physical device. When disabled (out-of-band), only the Morpheus inventory is updated.',
			),
		]
	}

	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return plugin
	}

	@Override
	String getCode() {
		return 'ilo-credential-type'
	}

	@Override
	String getName() {
		return 'iLO Credential Type'
	}
}
