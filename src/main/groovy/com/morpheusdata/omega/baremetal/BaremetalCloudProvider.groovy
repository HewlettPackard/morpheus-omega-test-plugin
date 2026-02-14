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
import com.morpheusdata.model.GenerateSupportBundleContentsRequest
import com.morpheusdata.model.Icon
import com.morpheusdata.model.NetworkSubnetType
import com.morpheusdata.model.NetworkType
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.ServerStatsData
import com.morpheusdata.model.StorageAggregateType
import com.morpheusdata.model.StorageControllerType
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.request.ValidateCloudRequest
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.omega.datasets.BaremetalResourcePoolDataSetProvider
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j

@Slf4j
class BaremetalCloudProvider implements CloudProvider, CloudProvider.CloudSupportBundleFacet {
	public static final String CLOUD_PROVIDER_CODE = 'omega.baremetal.cloud'
	public static final String STORAGE_AGGREGATE_TYPE_RAID1_CODE = 'omega.storage-aggregate-type.raid1'

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
	Collection<StorageVolumeType> getStorageVolumeTypes() {
		def storageVolumeTypes = [
			new StorageVolumeType(
				code: "omega.baremetal.raid0",
				externalId: 'omega_baremetal_raid0',
				displayName: "Omega Baremetal RAID 0",
				name: "RAID0",
				description: "Omega Baremetal - RAID 0",
				displayOrder: 1,
				defaultType: true,
				allowSearch: true,
				enabled: true,
				hasDatastore: false,
				resizable: false,
				planResizable: false
			)
		]

		storageVolumeTypes }

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
										new OptionType(
												name: 'num-disks',
												code: 'omega.baremetal.provision.num-disk',
												category: 'omega.baremetal.provision',
												inputType: OptionType.InputType.NUMBER,
												fieldName: 'numDisks',
												fieldContext: 'config',
												fieldLabel: 'Number of Disks',
												fieldGroup: 'advanced',
												displayOrder: 2,
												required: false,
												enabled: true,
												editable: false,
												global: false,
												custom: false,
												defaultValue: 2,
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

	@Override
	Collection<StorageAggregateType> getStorageAggregateTypes() {
		[new StorageAggregateType(
				name: 'RAID1',
				code: STORAGE_AGGREGATE_TYPE_RAID1_CODE
		)]
	}

	/**
	 * Provides cloud summary information for the Cloud -> Summary tab
	 * This demonstrates all three types of zone summary customization:
	 * 1. Standard info items
	 * 2. Custom zone summary renderer
	 * 3. Custom costing summary renderer
	 */
	@Override
	com.morpheusdata.model.CloudSummary getCloudSummary(Cloud cloud, com.morpheusdata.model.User user) {
		log.info("Getting cloud summary for cloud: ${cloud.name}")

		def summary = new com.morpheusdata.model.CloudSummary()

		// 1. Add standard info items
		def infoItems = []

		// Add custom fields to the standard info section
		def enableVnc = cloud.getConfigProperty('enableVnc')
		if (enableVnc != null) {
			infoItems << new com.morpheusdata.model.CloudSummaryInfoItem(
				'Hypervisor Console',
				null,
				enableVnc ? 'Enabled' : 'Disabled'
			)
		}

		def enableNetworkTypeSelection = cloud.getConfigProperty('enableNetworkTypeSelection')
		if (enableNetworkTypeSelection != null) {
			infoItems << new com.morpheusdata.model.CloudSummaryInfoItem(
				'Network Type Selection',
				null,
				enableNetworkTypeSelection ? 'Enabled' : 'Disabled'
			)
		}

		// Add a custom field showing cloud classification
		infoItems << new com.morpheusdata.model.CloudSummaryInfoItem(
			'Cloud Classification',
			null,
			getCloudClassification().toString()
		)

		summary.infoItems = infoItems

		// 2. Enable custom zone summary (optional - would require a CloudSummaryProvider)
		// summary.zoneSummaryRenderer = "baremetalZoneSummary"
		// summary.zoneSummaryData = [
		//     cloudId: cloud.id,
		//     cloudName: cloud.name
		// ]

		// 3. Enable custom costing summary (optional - would require a CloudCostingSummaryProvider)
		// if (hasCosting()) {
		//     summary.costingSummaryRenderer = "baremetalCostingSummary"
		//     summary.costingSummaryData = [
		//         cloudId: cloud.id
		//     ]
		// }

		return summary
	}

	/**
	 * Generate support bundle contents for this cloud/zone.
	 * This method is called when a support bundle is being generated and allows the provider
	 * to add custom diagnostic information specific to this cloud integration.
	 *
	 * @param cloud The cloud/zone to generate support bundle contents for
	 * @param request The request containing the target directory and resource info
	 * @return ServiceResponse indicating success or failure
	 */
	@Override
	ServiceResponse generateSupportBundleContents(Cloud cloud, GenerateSupportBundleContentsRequest request) {
		log.info("Generating support bundle contents for Baremetal cloud: ${cloud.name}")

		try {
			// Query resource pools first since we'll use this data
			def resourcePools = context.async.cloud.pool.listByCloudAndExternalIdIn(cloud.id, []).toList().blockingGet()

			// Add cloud-specific data to resourceInfo that looks like it came from external baremetal API
			request.resourceInfo.putAll([
				externalApiVersion: "v${new Random().nextInt(3) + 2}.${new Random().nextInt(10)}",
				hostsDiscovered: new Random().nextInt(50),
				hostsProvisioned: new Random().nextInt(30),
				hostsInMaintenance: new Random().nextInt(5),
				resourcePoolCount: resourcePools?.size() ?: 0,
				ipmiEnabled: true,
				redfishSupported: true,
				pxeBootAvailable: true,
				networkBootCapable: true,
				raidConfigurable: true,
				supportedRaidLevels: ['RAID0', 'RAID1', 'RAID5', 'RAID10'],
				hardwareVendors: ['HPE', 'Dell', 'Supermicro'],
				biosManagementAvailable: true,
				firmwareUpdateSupported: true,
				osTemplatesAvailable: new Random().nextInt(20),
				powerManagementProtocols: ['IPMI', 'Redfish', 'iLO'],
				remoteConsoleTypes: ['KVM', 'Serial'],
				lastInventorySync: new Date().format('yyyy-MM-dd HH:mm:ss'),
				syncStatus: 'completed',
				apiLatencyMs: new Random().nextInt(100) + 50
			])

			// Query and include related resource pools as a separate file
			if (resourcePools) {
				def poolsFile = request.contentsDir['resource-pools.json']
				def poolsData = resourcePools.collect { pool ->
					[
						id: pool.id,
						name: pool.name,
						externalId: pool.externalId,
						active: pool.active
					]
				}
				poolsFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(poolsData))
			}

			// Write fake logs that look like they were fetched from an external baremetal API
			def apiLogsFile = request.contentsDir['baremetal-api-logs.txt']
			apiLogsFile.text = """[2026-02-14 11:15:23] INFO  - Connected to Baremetal Management API at ${cloud.serviceUrl ?: 'https://baremetal.example.com'}
[2026-02-14 11:15:24] DEBUG - Authenticating with cloud: ${cloud.name}
[2026-02-14 11:15:24] INFO  - Authentication successful for account ${cloud.account?.id}
[2026-02-14 11:15:25] DEBUG - Fetching resource pool inventory...
[2026-02-14 11:15:26] INFO  - Found ${resourcePools?.size() ?: 0} resource pools
[2026-02-14 11:15:27] DEBUG - Querying available baremetal hosts...
[2026-02-14 11:15:28] INFO  - ${new Random().nextInt(50)} hosts discovered
[2026-02-14 11:15:29] DEBUG - Checking IPMI connectivity for hosts
[2026-02-14 11:15:30] INFO  - IPMI status: ${new Random().nextInt(45)} hosts responding
[2026-02-14 11:15:31] DEBUG - Syncing power state information
[2026-02-14 11:15:32] INFO  - Power state sync completed
[2026-02-14 11:15:33] DEBUG - Retrieving hardware inventory from BMC
[2026-02-14 11:15:34] INFO  - Hardware inventory updated successfully
[2026-02-14 11:15:35] DEBUG - Checking for pending provisioning tasks
[2026-02-14 11:15:36] INFO  - ${new Random().nextInt(10)} provisioning tasks in queue
[2026-02-14 11:15:37] INFO  - Cloud sync completed successfully
"""

			def provisioningLogFile = request.contentsDir['provisioning-status.log']
			provisioningLogFile.text = """Baremetal Provisioning Status Report
=====================================
Timestamp: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
Cloud: ${cloud.name}
Provider: ${CLOUD_PROVIDER_CODE}

Infrastructure Summary:
- Total Resource Pools: ${resourcePools?.size() ?: 0}
- Available Hosts: ${new Random().nextInt(50)}
- Provisioned Hosts: ${new Random().nextInt(30)}
- In Maintenance: ${new Random().nextInt(5)}

Network Configuration:
- VLAN Ranges: ${new Random().nextInt(100)}-${new Random().nextInt(100) + 100}
- DHCP Server: ACTIVE
- PXE Boot: ENABLED
- TFTP Server: OPERATIONAL

Storage Configuration:
- RAID Support: ENABLED
- Available Disk Types: SSD, HDD, NVMe
- Storage Controller: OK

Power Management:
- IPMI Enabled: YES
- Redfish Support: YES
- Wake-on-LAN: ENABLED
- Remote KVM: AVAILABLE

Provisioning Capabilities:
- OS Templates: ${new Random().nextInt(20)} available
- Custom ISOs: SUPPORTED
- Network Boot: ENABLED
- Automated Installation: ACTIVE

Last Successful Operations:
- Host Discovery: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
- Inventory Sync: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
- Power State Check: ${new Date().format('yyyy-MM-dd HH:mm:ss')}

Status: OPERATIONAL
"""

			log.info("Successfully generated support bundle contents for Baremetal cloud: ${cloud.name}")
			return ServiceResponse.success()

		} catch (Exception e) {
			log.error("Error generating support bundle contents for Baremetal cloud ${cloud.name}: ${e.message}", e)
			return ServiceResponse.error("Failed to generate support bundle contents: ${e.message}")
		}
	}
}
