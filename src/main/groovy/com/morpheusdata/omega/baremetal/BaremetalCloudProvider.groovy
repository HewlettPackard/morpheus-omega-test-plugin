package com.morpheusdata.omega.baremetal

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.CloudProvider
import com.morpheusdata.core.providers.ProvisionProvider
import com.morpheusdata.model.BackupProvider
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.ComputeDeviceType
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerType
import com.morpheusdata.model.Icon
import com.morpheusdata.model.NetworkSubnetType
import com.morpheusdata.model.NetworkType
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.ServerStatsData
import com.morpheusdata.model.StorageControllerType
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.request.ValidateCloudRequest
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.omega.datasets.BaremetalResourcePoolDataSetProvider
import groovy.util.logging.Slf4j

@Slf4j
class BaremetalCloudProvider implements CloudProvider {
	public static final String CLOUD_PROVIDER_CODE = 'omega.baremetal.cloud'

	protected MorpheusContext context
	protected Plugin plugin

	BaremetalCloudProvider(Plugin plugin, MorpheusContext ctx) {
		super()
		this.@plugin = plugin
		this.@context = ctx
	}

	@Override
	Boolean canCreateCloudPools() {
		return true
	}

	@Override
	Boolean canDeleteCloudPools() {
		return true
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

	@Override
	Collection<ComputeDeviceType> getComputeDeviceTypes() {
		[
		    new ComputeDeviceType(
						family: ComputeDeviceType.Family.GPU,
						code: 'omega.baremetal.gpu',
						hotpluggable: false,
						productId: 1337,
						vendorId: 1337,
						busType: 'pci',
						name: 'Omega Baremetal GPU',
						assignable: true,
				),
		]
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
				),
				new OptionType(
						name: 'Enable Hypervisor Console',
						code: 'omega.baremetal.enable-hypervisor-console',
						displayOrder: 9,
						fieldContext: 'config',
						fieldName: 'enableVnc',
						fieldLabel: 'Enable Hypervisor Console',
						fieldGroup: 'Advanced',
						inputType: OptionType.InputType.CHECKBOX,
						defaultValue: true,
				)
		]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<ProvisionProvider> getAvailableProvisionProviders() {
		return (this.@plugin.getProvidersByType(ProvisionProvider) as Collection<ProvisionProvider>).findAll{
			it.code in [
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
						provisionTypeCode: 'omega.baremetal.provision',
						selectable: false,
						supportsConsoleKeymap: true,
						vmHypervisor: false,
						hasDevices: true, // This is required to show the 'Devices' tab in the UI for a compute server
						supportsDeviceAttachment: false, // This is the default but to make it clear, this is a baremetal server and can't attach/detach devices
						optionTypes: [
										new OptionType(
												code: 'serverType.omega.resourcePool',
												inputType: OptionType.InputType.SELECT,
												name: 'Resource Pool',
												fieldName: 'resourcePoolId',
												fieldLabel: 'Resource Pool',
												fieldContext: 'config',
												required: true,
												defaultValue: '',
												displayOrder: 0,
												editable: false,
												optionSourceType: BaremetalResourcePoolDataSetProvider.PROVIDER_NAMESPACE,
												optionSource: BaremetalResourcePoolDataSetProvider.PROVIDER_KEY
										),
										new OptionType(
												name: 'iLO Server IP',
												code: 'omega.baremetal.provision.ilo-server-ip',
												category: 'omega.baremetal.manual-provision',
												inputType: OptionType.InputType.TEXT,
												fieldName: 'consoleHost',
												fieldContext: 'config',
												fieldLabel: 'iLO Server IP',
												displayOrder: 1,
												required: false,
												enabled: true,
												editable: false,
												global: false,
												custom: false,
										),
										new OptionType(
												name: 'iLO Server IP',
												code: 'omega.baremetal.provision.ilo-server-name',
												category: 'omega.baremetal.provision',
												inputType: OptionType.InputType.TEXT,
												fieldName: 'consoleUsername',
												fieldContext: 'config',
												fieldLabel: 'iLO Server Username',
												displayOrder: 2,
												required: false,
												enabled: true,
												editable: false,
												global: false,
												custom: false,
										),
										new OptionType(
												name: 'iLO Server Password',
												code: 'omega.baremetal.provision.ilo-server-password',
												category: 'omega.baremetal.provision',
												inputType: OptionType.InputType.PASSWORD,
												fieldName: 'consolePassword',
												fieldContext: 'config',
												fieldLabel: 'iLO Server Password',
												displayOrder: 3,
												required: false,
												enabled: true,
												editable: false,
												global: false,
												custom: false,
										),
										new OptionType(
												name: 'Pre-provisioned',
												code: 'omega.baremetal.provision.pre-provisioned',
												category: 'omega.baremetal.provision',
												inputType: OptionType.InputType.CHECKBOX,
												fieldName: 'preProvisioned',
												fieldContext: 'config',
												fieldLabel: 'Pre-provisioned',
												displayOrder: 4,
												required: false,
												enabled: true,
												editable: false,
												global: false,
												custom: false,
												helpText: "Indicates if this server is already provisioned with an OS. Allows for convert to managed.",
										),
										new OptionType(
												name: 'num-nics',
												code: 'omega.baremetal.provision.num-nic',
												category: 'omega.baremetal.provision',
												inputType: OptionType.InputType.NUMBER,
												fieldName: 'numNics',
												fieldContext: 'config',
												fieldLabel: 'Number of NICs',
												fieldGroup: 'advanced',
												displayOrder: 1,
												required: false,
												enabled: true,
												editable: false,
												global: false,
												custom: false,
												defaultValue: 4,
										),
						]
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
		return true
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
	 *
	 * Implementation of getServerStats for testing purposes.
	 * This generates mock/stub server statistics data to demonstrate the feature.
	 * In a real implementation, this would call the cloud provider's monitoring API.
	 */
	@Override
	List<ServerStatsData> getServerStats(ComputeServer computeServer, Map<String, Object> opts) {
		log.info("getServerStats called for server: ${computeServer.id} - ${computeServer.name}")

		List<ServerStatsData> statsResults = []

		try {
			// Generate mock stats for the last hour with data points every 5 minutes
			Date endDate = new Date()
			Date startDate = new Date(endDate.time - (60 * 60 * 1000)) // 1 hour ago

			// Create 12 data points (one every 5 minutes)
			for (int i = 0; i < 12; i++) {
				Date timestamp = new Date(startDate.time + (i * 5 * 60 * 1000))

				// Generate some random-looking but realistic stats
				Float cpuUsage = 20.0f + (Math.random() * 60.0f) // Random CPU between 20-80%
				Long totalMemory = computeServer.maxMemory ?: 8589934592L // 8GB default
				Long usedMemory = (totalMemory * (0.3 + Math.random() * 0.4)) as Long // 30-70% used
				Long freeMemory = totalMemory - usedMemory

				Long totalStorage = computeServer.maxStorage ?: 107374182400L // 100GB default
				Long usedStorage = (totalStorage * (0.4 + Math.random() * 0.3)) as Long // 40-70% used
				Long freeStorage = totalStorage - usedStorage

				ServerStatsData statsData = new ServerStatsData()
				statsData.id = computeServer.id
				statsData.date = timestamp
				statsData.cpuUsage = cpuUsage
				statsData.usedMemory = usedMemory
				statsData.maxMemory = totalMemory
				statsData.freeMemory = freeMemory
				statsData.usedStorage = usedStorage
				statsData.maxStorage = totalStorage
				statsData.freeStorage = freeStorage
				statsData.running = computeServer.powerState == 'on'

				statsResults.add(statsData)

				log.debug("Generated stats for ${timestamp}: CPU=${cpuUsage}%, Memory=${usedMemory}/${totalMemory}, Running=${statsData.running}")
			}

			log.info("Generated ${statsResults.size()} stats data points for server ${computeServer.id}")

		} catch (Exception e) {
			log.error("Error generating server stats for ${computeServer.id}: ${e.message}", e)
		}

		return statsResults
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

	@Override
	Boolean hasSecurityGroups() {
		return false
	}

    @Override
    Boolean provisionRequiresResourcePool() {
        return true;
    }
}

