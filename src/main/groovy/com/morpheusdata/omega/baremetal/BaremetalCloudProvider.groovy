package com.morpheusdata.omega.baremetal

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

class BaremetalCloudProvider implements CloudProvider {
	public static final String CLOUD_PROVIDER_CODE = 'omega.baremetal.cloud'

	protected MorpheusContext context
	protected Plugin plugin

	BaremetalCloudProvider(Plugin plugin, MorpheusContext ctx) {
		super()
		this.@plugin = plugin
		this.@context = ctx
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	CloudClassification getCloudClassification() {
		return CloudClassification.PRIVATE
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getDescription() {
		return 'An example cloud plugin for supporting baremetal'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getIcon() {
		return new Icon(path:'omega.svg', darkPath:'omega-dark.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getCircularIcon() {
		return new Icon(path:'omega-circular.svg', darkPath:'omega-circular-dark.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<OptionType> getOptionTypes() {
		[
				// Since we have multiple network types, we need to add this to the cloud so we can see them during provisioning
				new OptionType(
						name: 'Enable Network Type Selection',
						code: 'omega.baremetal.enable-network-type-selection',
						displayOrder: 8,
						fieldContext: 'config',
						fieldName: 'enableNetworkTypeSelection',
						fieldCode: 'gomorpheus.label.enableNetworkTypeSelection',
						fieldGroup: 'Advanced',
						inputType: OptionType.InputType.CHECKBOX,
						defaultValue: true,
				)]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<ProvisionProvider> getAvailableProvisionProviders() {
		return (this.@plugin.getProvidersByType(ProvisionProvider) as Collection<ProvisionProvider>).findAll{
			it.code in [
					BaremetalManualProvisionProvider.PROVISION_PROVIDER_CODE,
					BaremetalProvisionProvider.PROVISION_PROVIDER_CODE,
			]
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<BackupProvider> getAvailableBackupProviders() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<NetworkType> getNetworkTypes() {
		[
				new NetworkType([
						code				: 'omega.baremetal.network',
						name				: 'Omega Baremetal - Unmanaged Network',
						description			: '',
						overlay				: false,
						externalType		: 'External',
						creatable			: true,
						cidrEditable		: true,
						dhcpServerEditable	: true,
						dnsEditable			: true,
						gatewayEditable		: true,
						cidrRequired		: false,
						vlanIdEditable		: true,
						canAssignPool		: true,
						hasNetworkServer	: false,
						hasCidr				: true
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
						agentType: ComputeServerType.AgentType.guest,
						bareMetalHost: true,
						code: 'omega.baremetal.stub-server',
						computeService: null,
						containerHypervisor: false,
						controlPower: true,
						controlSuspend: false,
						creatable: true,
						description: 'A stubbed server that has no real backing server.',
						displayOrder: 99,
						enabled: true,
						externalDelete: true,
						guestVm: false,
						hasAutomation: false,
						managed: false,
						name: 'Omega Baremetal Stub Server',
						platform: PlatformType.none,
						provisionTypeCode: 'omega.baremetal.manual-provision',
						selectable: false,
						supportsConsoleKeymap: true,
						vmHypervisor: false,
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
		return true
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
		return true
	}

	/**
	 * {@inheritDoc}
	 *
	 * Must set this to true to flag this cloud has baremetal resources. This makes the 'Baremetal' tab to show in the UI.
	 */
	@Override
	Boolean hasBareMetal() {
		return true
	}

	/**
	 * {@inheritDoc} 
	 */
	@Override
	Boolean hasCloudInit() {
		return true
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
		return BaremetalProvisionProvider.PROVISION_PROVIDER_CODE
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
		return 'Omega Baremetal'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<String> getSupportedNetworkProviderCodes() { [ 'morpheus-arubacx-network' ] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean canCreateNetworks() { true }

	@Override
	Boolean canCreateDatastores() {
		return true
	}
}
