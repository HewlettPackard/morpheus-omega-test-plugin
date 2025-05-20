package com.morpheusdata.omega.process

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.CloudProvider
import com.morpheusdata.core.providers.ProvisionProvider
import com.morpheusdata.model.BackupProvider
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerType
import com.morpheusdata.model.Icon
import com.morpheusdata.model.NetworkSubnetType
import com.morpheusdata.model.NetworkType
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.StorageControllerType
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.request.ValidateCloudRequest
import com.morpheusdata.response.ServiceResponse

class ProcessServiceExampleCloudProvider implements CloudProvider {
	public static final String CLOUD_PROVIDER_CODE = 'omega.process.cloud'

	protected MorpheusContext context
	protected Plugin plugin

	ProcessServiceExampleCloudProvider(Plugin plugin, MorpheusContext ctx) {
		super()
		this.@plugin = plugin
		this.@context = ctx
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getDescription() {
		return 'An example cloud plugin showing how to use the ProcessService APIs.'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getIcon() {
		return new Icon(path:'morpheus.svg', darkPath:'morpheus.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getCircularIcon() {
		// TODO: change icon paths to correct filenames once added to your project
		return new Icon(path:'cloud-circular.svg', darkPath:'cloud-circular-dark.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<OptionType> getOptionTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<ProvisionProvider> getAvailableProvisionProviders() {
	    return this.@plugin.getProvidersByType(ProvisionProvider) as Collection<ProvisionProvider>
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<BackupProvider> getAvailableBackupProviders() { [] }

	/**
	 * {@inheritDoc}
	 *
	 * We at least need a single network to use during provisioning
	 */
	@Override
	Collection<NetworkType> getNetworkTypes() {
		[
				new NetworkType([
						code: 'omega.process.network',
						name: 'Process Service Example - Unmanaged Network',
						description: '',
						overlay: false,
						externalType: 'External',
						creatable: true,
						cidrEditable: true,
						dhcpServerEditable: true,
						dnsEditable: true,
						gatewayEditable: true,
						cidrRequired: false,
						vlanIdEditable: true,
						canAssignPool: true,
						hasNetworkServer: false,
						hasCidr: true
				])
		]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<NetworkSubnetType> getSubnetTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<StorageVolumeType> getStorageVolumeTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<StorageControllerType> getStorageControllerTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<ComputeServerType> getComputeServerTypes() {
		[
		    new ComputeServerType(
						code: 'omega.process.compute-server-type',
						name: 'Process Service Example Server',
						description: '',
						platform: PlatformType.none,
						agentType: ComputeServerType.AgentType.guest,
						enabled: true,
						selectable: false,
						externalDelete: true,
						managed: false,
						controlPower: true,
						controlSuspend: false,
						creatable: true,
						computeService: null,
						displayOrder: 1,
						hasAutomation: false,
						provisionTypeCode: ProcessServiceExampleProvisionProvider.PROVISION_PROVIDER_CODE,
						containerHypervisor: false,
						bareMetalHost: true,
						vmHypervisor: false,
						guestVm: false,
						supportsConsoleKeymap: true,
				)
		]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse validate(Cloud cloud, ValidateCloudRequest validateCloudRequest) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse initializeCloud(Cloud cloud) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse refresh(Cloud cloud) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void refreshDaily(Cloud cloud) {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse deleteCloud(Cloud cloud) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasComputeZonePools() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasNetworks() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasFolders() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasDatastores() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasBareMetal() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasCloudInit() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean supportsDistributedWorker() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse startServer(ComputeServer computeServer) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse stopServer(ComputeServer computeServer) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse deleteServer(ComputeServer computeServer) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ProvisionProvider getProvisionProvider(String providerCode) {
		return getAvailableProvisionProviders().find { it.code == providerCode }
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getDefaultProvisionTypeCode() {
		return ProcessServiceExampleProvisionProvider.PROVISION_PROVIDER_CODE
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.@context
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
		return CLOUD_PROVIDER_CODE
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getName() {
		return 'Process Service Example Cloud'
	}
}
