package com.morpheusdata.omega.ipam

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.IPAMProvider
import com.morpheusdata.model.Icon
import com.morpheusdata.model.NetworkDomain
import com.morpheusdata.model.NetworkPool
import com.morpheusdata.model.NetworkPoolIp
import com.morpheusdata.model.NetworkPoolServer
import com.morpheusdata.model.NetworkPoolType
import com.morpheusdata.model.OptionType
import com.morpheusdata.omega.datasets.OmegaIpamPoolServerDatasetProvider
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.response.ServiceResponse
import groovy.json.JsonOutput

/**
 * Omega test IPAM provider. Implements the full {@link IPAMProvider}
 *
 * All operations are no-ops that log their invocation and return success — this is intentional
 * for a test/demo plugin.
 */
class OmegaIpamProvider implements IPAMProvider {

	public static final String IPAM_PROVIDER_CODE = 'omega.ipam'

	protected MorpheusContext morpheusContext
	protected Plugin plugin
	protected final LogWrapper log = LogWrapper.instance

	OmegaIpamProvider(Plugin plugin, MorpheusContext morpheusContext) {
		super()
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

  /**
   * {@inheritDoc}
   */
	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

  /**
   * {@inheritDoc}
   */
	@Override
	Plugin getPlugin() {
		return plugin
	}

  /**
   * {@inheritDoc}
   */
	@Override
	String getCode() {
		return IPAM_PROVIDER_CODE
	}

  /**
   * {@inheritDoc}
   */
	@Override
	String getName() {
		return 'Omega Test IPAM'
	}

  /**
   * {@inheritDoc}
   */
	@Override
	Icon getIcon() {
		return new Icon(path: 'omega.svg', darkPath: 'omega-dark.svg')
	}

  /**
   * {@inheritDoc}
   */
	@Override
	List<OptionType> getIntegrationOptionTypes() {
		return [
			new OptionType(
				code: 'omega.ipam.serviceUrl', name: 'serviceUrl',
				category: 'networkPoolServer.omega.ipam',
				fieldName: 'serviceUrl', fieldCode: 'gomorpheus.optiontype.Url', fieldLabel: 'URL',
				fieldContext: 'domain', required: true, enabled: true, editable: true, global: false,
				displayOrder: 0
			),
			new OptionType(
				code: 'omega.ipam.credential', name: 'credentials',
				optionSource: 'credentials',
				category: 'networkPoolServer.omega.ipam',
				fieldName: 'type', fieldCode: 'gomorpheus.label.credentials', fieldLabel: 'Credentials',
				fieldContext: 'credential', required: true, enabled: true, editable: true, global: false,
				displayOrder: 5, defaultValue: 'local',
				config: JsonOutput.toJson(credentialTypes: ['username-password'])
			),
			new OptionType(
				code: 'omega.ipam.serviceUsername', name: 'serviceUsername',
				category: 'networkPoolServer.omega.ipam',
				fieldName: 'serviceUsername', fieldCode: 'gomorpheus.optiontype.Username', fieldLabel: 'Username',
				fieldContext: 'domain', required: false, enabled: true, editable: true, global: false,
				displayOrder: 10, localCredential: true
			),
			new OptionType(
				code: 'omega.ipam.servicePassword', name: 'servicePassword',
				category: 'networkPoolServer.omega.ipam',
				fieldName: 'servicePassword', fieldCode: 'gomorpheus.optiontype.Password', fieldLabel: 'Password',
				fieldContext: 'domain', required: false, enabled: true, editable: true, global: false,
				displayOrder: 15, localCredential: true
			)
		]
	}

  /**
   * {@inheritDoc}
   */
	@Override
	Collection<NetworkPoolType> getNetworkPoolTypes() {
		return [
			new NetworkPoolType(
				code: 'omega.ipam.pool',
				name: 'Omega IPAM Pool',
				creatable: true,
				description: 'Omega test IPAM pool type'
			)
		]
	}

	@Override
	List<OptionType> getNetworkPoolOptionTypes() {
		return [
			new OptionType(
				code: 'omega.ipam.pool.poolServer',
				name: 'poolServer',
				category: 'networkPool.omega.ipam.pool',
				fieldName: 'poolServer',
				fieldCode: 'gomorpheus.label.integration',
				fieldLabel: 'Integration',
				fieldContext: 'domain',
				inputType: OptionType.InputType.SELECT,
				required: true,
				enabled: true,
				editable: true,
				global: false,
				noBlank: true,
				noSelection: 'Select',
				displayOrder: 0,
				optionSourceType: OmegaIpamPoolServerDatasetProvider.PROVIDER_NAMESPACE,
				optionSource: OmegaIpamPoolServerDatasetProvider.PROVIDER_KEY
			)
		]
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse verifyNetworkPoolServer(NetworkPoolServer poolServer, Map opts) {
		log.info("OmegaIpamProvider.verifyNetworkPoolServer: poolServer=${poolServer?.id}")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse createNetworkPoolServer(NetworkPoolServer poolServer, Map opts) {
		log.info("OmegaIpamProvider.createNetworkPoolServer: poolServer=${poolServer?.id}")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse updateNetworkPoolServer(NetworkPoolServer poolServer, Map opts) {
		log.info("OmegaIpamProvider.updateNetworkPoolServer: poolServer=${poolServer?.id}")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse initializeNetworkPoolServer(NetworkPoolServer poolServer, Map opts) {
		log.info("OmegaIpamProvider.initializeNetworkPoolServer: poolServer=${poolServer?.id}")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	void refresh(NetworkPoolServer poolServer) {
		log.info("OmegaIpamProvider.refresh: poolServer=${poolServer?.id}")
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse createNetworkPool(NetworkPoolServer poolServer, NetworkPool networkPool, Map opts) {
		log.info("OmegaIpamProvider.createNetworkPool: poolServer=${poolServer?.id}, networkPool=${networkPool?.id} (${networkPool?.name})")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse updateNetworkPool(NetworkPoolServer poolServer, NetworkPool networkPool, Map opts) {
		log.info("OmegaIpamProvider.updateNetworkPool: poolServer=${poolServer?.id}, networkPool=${networkPool?.id} (${networkPool?.name})")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse deleteNetworkPool(NetworkPoolServer poolServer, NetworkPool networkPool, Map opts) {
		log.info("OmegaIpamProvider.deleteNetworkPool: poolServer=${poolServer?.id}, networkPool=${networkPool?.id} (${networkPool?.name})")
		return ServiceResponse.success()
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse createHostRecord(NetworkPoolServer poolServer, NetworkPool networkPool, NetworkPoolIp networkPoolIp, NetworkDomain domain, Boolean createARecord, Boolean createPtrRecord) {
		log.info("OmegaIpamProvider.createHostRecord: pool=${networkPool?.id}, ip=${networkPoolIp?.ipAddress}")
		// Auto-assign a fake IP if none provided, to satisfy the contract
		if (!networkPoolIp.ipAddress) {
			networkPoolIp.ipAddress = '10.0.0.1'
		}
		return ServiceResponse.success(networkPoolIp)
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse updateHostRecord(NetworkPoolServer poolServer, NetworkPool networkPool, NetworkPoolIp networkPoolIp) {
		log.info("OmegaIpamProvider.updateHostRecord: pool=${networkPool?.id}, ip=${networkPoolIp?.ipAddress}")
		return ServiceResponse.success(networkPoolIp)
	}

  /**
   * {@inheritDoc}
   */
	@Override
	ServiceResponse deleteHostRecord(NetworkPool networkPool, NetworkPoolIp poolIp, Boolean deleteAssociatedRecords) {
		log.info("OmegaIpamProvider.deleteHostRecord: pool=${networkPool?.id}, ip=${poolIp?.ipAddress}")
		return ServiceResponse.success()
	}
}
