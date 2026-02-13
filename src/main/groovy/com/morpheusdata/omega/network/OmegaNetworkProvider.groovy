package com.morpheusdata.omega.network

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.NetworkProvider
import com.morpheusdata.model.GenerateSupportBundleContentsRequest
import com.morpheusdata.model.Icon
import com.morpheusdata.model.Network
import com.morpheusdata.model.NetworkRouterType
import com.morpheusdata.model.NetworkServer
import com.morpheusdata.model.NetworkSubnet
import com.morpheusdata.model.NetworkType
import com.morpheusdata.model.OptionType
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.Renderer
import groovy.json.JsonOutput

class OmegaNetworkProvider implements NetworkProvider, NetworkProvider.NetworkServerSupportBundleFacet {

	public static final String NETWORK_PROVIDER_CODE = 'omega.network'

	protected MorpheusContext morpheusContext
	protected Plugin plugin
	protected final LogWrapper log = LogWrapper.instance

	OmegaNetworkProvider(Plugin plugin, MorpheusContext morpheusContext) {
		super()
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getIcon() {
		return new Icon(path:'omega.svg', darkPath:'omega-dark.svg')
	}

	@Override
	String getDescription() {
		return 'This is a custom network provider for Morpheus Omega Test Plugin.'
	}

	@Override
	Boolean getCreatable() {
		return true
	}

	@Override
	Boolean isUserVisible() {
		return true
	}

	@Override
	Collection<NetworkType> getNetworkTypes() {
		def networkType = new NetworkType()
		networkType.code = 'omega.network'
		networkType.name = 'Omega Test Network'
		networkType.description = 'Test network type for Omega plugin'
		networkType.creatable = true
		networkType.deletable = true
		return [networkType]
	}

	@Override
	Collection<NetworkRouterType> getRouterTypes() {
		return []
	}

	@Override
	Collection<OptionType> getOptionTypes() {
		return []
	}

	@Override
	String getNetworkServerTypeCode() {
		return NETWORK_PROVIDER_CODE
	}

	@Override
	ServiceResponse<Network> createNetwork(Network network, Map opts) {
		return ServiceResponse.success(network)
	}

	@Override
	ServiceResponse<Network> updateNetwork(Network network, Map opts) {
		return ServiceResponse.success(network)
	}

	@Override
	ServiceResponse deleteNetwork(Network network, Map opts) {
		return ServiceResponse.success()
	}

	@Override
	ServiceResponse<NetworkSubnet> createSubnet(NetworkSubnet networkSubnet, Network network, Map opts) {
		return ServiceResponse.success(networkSubnet)
	}

	@Override
	ServiceResponse<NetworkSubnet> updateSubnet(NetworkSubnet networkSubnet, Network network, Map opts) {
		return ServiceResponse.success(networkSubnet)
	}

	@Override
	ServiceResponse deleteSubnet(NetworkSubnet networkSubnet, Network network, Map opts) {
		return ServiceResponse.success()
	}

	@Override
	Renderer<?> getRenderer() {
		return null
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
		return NETWORK_PROVIDER_CODE
	}

	@Override
	String getName() {
		return "Omega Network Server"
	}

	/**
	 * Generate support bundle contents for this network server.
	 *
	 * @param networkServer The network server to generate support bundle contents for
	 * @param request The request containing the target directory and resource info
	 * @return ServiceResponse indicating success or failure
	 */
	@Override
	ServiceResponse generateSupportBundleContents(NetworkServer networkServer, GenerateSupportBundleContentsRequest request) {
		log.info("Generating support bundle contents for Omega network server: ${networkServer.name}")

		try {
			def contentsDir = request.contentsDir

			// Add network server configuration details
			def serverConfigFile = contentsDir['omega-network-server-config.json']
			def serverConfig = [
				serverId      : networkServer.id,
				serverName    : networkServer.name,
				serverType    : networkServer.type?.name,
				status        : networkServer.status,
				statusMessage : networkServer.statusMessage,
				serviceUrl    : networkServer.serviceUrl,
				enabled       : networkServer.enabled,
				accountId     : networkServer.account?.id,
				createdDate   : networkServer.dateCreated?.toString(),
				lastUpdated   : networkServer.lastUpdated?.toString()
			]
			serverConfigFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(serverConfig))

			// Add provider diagnostics
			def diagnosticsFile = contentsDir['omega-network-diagnostics.json']
			def diagnostics = [
				providerCode: getCode(),
				providerName: getName(),
				description : getDescription(),
				timestamp   : new Date().toString()
			]
			diagnosticsFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(diagnostics))

			log.info("Successfully generated support bundle contents for Omega network server: ${networkServer.name}")
			return ServiceResponse.success()

		} catch (Exception e) {
			log.error("Error generating support bundle contents for Omega network server ${networkServer.name}: ${e.message}", e)
			return ServiceResponse.error("Failed to generate support bundle contents: ${e.message}")
		}
	}
}
