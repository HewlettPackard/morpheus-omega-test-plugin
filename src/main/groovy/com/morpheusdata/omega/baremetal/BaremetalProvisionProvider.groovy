package com.morpheusdata.omega.baremetal

import com.morpheusdata.PrepareHostResponse
import com.morpheusdata.core.AbstractProvisionProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.ProvisionInstanceServers
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.NullDataFilter
import com.morpheusdata.core.providers.HostProvisionProvider
import com.morpheusdata.core.providers.ProvisionProvider
import com.morpheusdata.core.providers.ResourceProvisionProvider
import com.morpheusdata.core.providers.WorkloadProvisionProvider
import com.morpheusdata.model.ComputeDevice
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerInterface
import com.morpheusdata.model.ComputeServerInterfaceType
import com.morpheusdata.model.GenerateSupportBundleContentsRequest
import com.morpheusdata.model.Icon
import com.morpheusdata.model.Instance
import com.morpheusdata.model.NetAddress
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ProvisionType
import com.morpheusdata.model.ServicePlan
import com.morpheusdata.model.Snapshot
import com.morpheusdata.model.StorageAggregate
import com.morpheusdata.model.StorageVolume
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.model.VirtualImageType
import com.morpheusdata.model.Workload
import com.morpheusdata.model.provisioning.HostRequest
import com.morpheusdata.model.provisioning.InstanceRequest
import com.morpheusdata.model.provisioning.RemoveWorkloadRequest
import com.morpheusdata.model.provisioning.WorkloadRequest
import com.morpheusdata.request.AfterConvertToManagedRequest
import com.morpheusdata.request.BeforeConvertToManagedRequest
import com.morpheusdata.request.CreateSnapshotRequest
import com.morpheusdata.request.ResizeRequest
import com.morpheusdata.request.ResizeV2Request
import com.morpheusdata.response.AfterConvertToManagedResponse
import com.morpheusdata.response.BeforeConvertToManagedResponse
import com.morpheusdata.response.PrepareInstanceResponse
import com.morpheusdata.response.PrepareResizeV2WorkloadResponse
import com.morpheusdata.response.PrepareWorkloadResponse
import com.morpheusdata.response.ProvisionResponse
import com.morpheusdata.response.ResizeV2WorkloadResponse
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.response.ValidateResizeV2WorkloadResponse
import com.morpheusdata.omega.datasets.BaremetalHostsDataSetProvider
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j

/**
 * Provision provider for provisioning baremetal servers. This picks from the pool of servers in an 'available'
 * state.
 */
@Slf4j
class BaremetalProvisionProvider extends AbstractProvisionProvider
		implements WorkloadProvisionProvider, ProvisionInstanceServers, ProvisionProvider.HypervisorConsoleFacet,
				WorkloadProvisionProvider.ResizeV2Facet, HostProvisionProvider, HostProvisionProvider.ResizeV2Facet, HostProvisionProvider.finalizeHostFacet,  ProvisionProvider.SnapshotFacet,
				ProvisionProvider.ConvertToManagedFacet, ResourceProvisionProvider, HostProvisionProvider.HostSupportBundleFacet {
	public static final String PROVISION_PROVIDER_CODE = 'omega.baremetal.provision'
	public static final String ALLETRA_STORAGE_TYPE_CODE = 'hpealletraMPLUN'
	public static final String CSI_VLAN_CODE = "omega.baremetal.csi.vlan"
	public static final String CSI_PHYS_CODE = "omega.baremetal.csi.phys"
	public static final String CSI_BOND_CODE = "omega.baremetal.csi.bond"
	public static final String SERVER_LOCK_ID = "omega.baremetal.getServerLock"

	protected MorpheusContext context
	protected Plugin plugin

	BaremetalProvisionProvider(Plugin plugin, MorpheusContext ctx) {
		super()
		this.@context = ctx
		this.@plugin = plugin
	}

	/**
	 * {@inheritDoc}
	 *
	 * This makes it so we skip network wait since we're using stubbed servers.
	 */
	@Override
	Boolean supportsAgent() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<PrepareWorkloadResponse> prepareWorkload(Workload workload, WorkloadRequest workloadRequest, Map opts) {
		ServiceResponse<PrepareWorkloadResponse> resp = new ServiceResponse<PrepareWorkloadResponse>(
				true, // successful
				'', // no message
				null, // no errors
				new PrepareWorkloadResponse(workload:workload) // adding the workload to the response for convenience
		)
		return resp
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getProvisionTypeCode() {
		return PROVISION_PROVIDER_CODE
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
		def options = []
		options << new OptionType(
				name: "Baremetal Hosts",
				code: 'provisionType.omega.hosts',
				displayOrder: 20,
				fieldContext: 'config',
				fieldName: 'hosts',
				fieldLabel: 'Baremetal Host(s)',
				fieldGroup: 'Options',
				fieldCode: 'gomorpheus.optiontype.bmSelector',
				inputType: OptionType.InputType.MULTI_SELECT,
				dependsOn: 'config.resourcePoolId,plan.id',
				optionSourceType: BaremetalHostsDataSetProvider.PROVIDER_NAMESPACE,
				optionSource: BaremetalHostsDataSetProvider.PROVIDER_KEY,
				helpText: 'Find and select one or more baremetal hosts to provision this workload on',
		)
		options
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<OptionType> getNodeOptionTypes() { [] }


	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<StorageVolumeType> getRootVolumeStorageTypes() {
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

		storageVolumeTypes
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<StorageVolumeType> getDataVolumeStorageTypes() {
		def dataVolTypes = []
		StorageVolumeType alletraVolType = context.services.storage.volume.storageVolumeType.find(new DataQuery().withFilter("code", ALLETRA_STORAGE_TYPE_CODE))

		if (alletraVolType) {
			dataVolTypes << alletraVolType
		}

		return dataVolTypes
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<ServicePlan> getServicePlans() {
		[
				new ServicePlan(
						code: 'omega.baremetal.any',
						editable: true,
						name: 'Omega Baremetal Stub',
						description: 'Any Server',
						sortOrder: 0,
						maxCores: 1,
						maxCpu: 1,
						maxMemory: 0,
						maxStorage: 6871947673600,
						customMaxStorage: true,
						customMaxDataStorage: true,
						addVolumes: true,
				),
				// Second plan introduced to test plan selection
				// bare metal server selection during instance
				// provisioning based on plan choice.
				new ServicePlan(
						code: 'omega.baremetal.any2',
						editable: true,
						name: 'Omega Baremetal Stub2',
						description: 'Any Server',
						sortOrder: 0,
						maxCores: 1,
						maxCpu: 1,
						maxMemory: 0,
						maxStorage: 6871947673600,
						customMaxStorage: true,
						customMaxDataStorage: true,
						addVolumes: true,
				)
		]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse validateWorkload(Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> runWorkload(Workload workload, WorkloadRequest workloadRequest, Map opts) {
		StorageAggregate aggregate = new StorageAggregate(
				uuid: "${UUID.randomUUID().toString()}",
				refType: "ComputeServer",
				refId: workload.server.id,
				name: "Raid0",
				type: context.services.storage.aggregate.storageAggregateType.find(
						new DataQuery().withFilter('code', BaremetalCloudProvider.STORAGE_AGGREGATE_TYPE_RAID1_CODE)
				)
		)

		aggregate.members = workload.server.volumes.findAll{
			it.type.code == 'standard'
		}
		aggregate.volumes = [workload.server.volumes.find{
			it.type.code == 'omega.baremetal.raid0'
		}]
		context.services.storage.aggregate.create(aggregate)

		// update root raid volume (it's the only type it can be) to the size of all the member disks
		def rootRaidVolume = context.services.storage.volume.get(aggregate.volumes.first().id)
		rootRaidVolume.maxStorage = (Long) aggregate.members.sum {it.maxStorage}
		context.services.storage.volume.save(rootRaidVolume)

		return new ServiceResponse<ProvisionResponse>(
				true,
				null, // no message
				null, // no errors
				new ProvisionResponse(success:true, installAgent: false, skipNetworkWait: true, noAgent: true)
		)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse finalizeWorkload(Workload workload) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse stopWorkload(Workload workload) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse startWorkload(Workload workload) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse restartWorkload(Workload workload) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 *
	 * Removes a workload from our instance. Since our server will be long lived, we make sure to clean up any
	 * anything that might have been adjusted.
	 */
	@Override
	ServiceResponse removeWorkload(Workload workload, RemoveWorkloadRequest request) {
		workload.server.interfaces
				.findAll { (!it.type.deleteOnWorkloadRemoval) }
				.sort { it.displayOrder }
				.eachWithIndex { it, i ->
					it.publicIpAddress = null
					it.addresses = null
					it.network = null
					it.dhcp = false
					it.primaryInterface = false
					it.subnet = null
					it.networkDomain = null
					it.networkPool = null
					it.name = "n/a"
				}
		context.services.computeServer.computeServerInterface.bulkSave(workload.server.interfaces)

		// Clear out the volume that was created as our 'root' volume during provisioning.
		def rootVol  = workload.server.volumes.find { it.rootVolume && !it.datastore }
		if (rootVol) {

			// remove any aggregates creating during runWorkload
			def aggregates = context.services.storage.aggregate.list(new DataQuery().withFilters(
					new DataFilter('refId', workload.server.id),
					new DataFilter('refType', 'ComputeServer')
			))
			aggregates.each {
				it.volumes = []
				it.members = []
			}
			context.services.storage.aggregate.bulkSave(aggregates)
			context.services.storage.aggregate.bulkRemove(aggregates)
			context.async.storageVolume.remove([rootVol], workload.server, true).blockingGet()
			workload.server = context.services.computeServer.get(workload.server.id)
		}

		// mark the underlying server available again so it returns to the pool of available servers.
		workload.server.status = 'available'
		context.services.computeServer.save(workload.server)
		return ServiceResponse.success(['removeServer': false, 'preserveVolumes': true])
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> getServerDetails(ComputeServer server) {
		return new ServiceResponse<ProvisionResponse>(true, null, null, new ProvisionResponse(success:true))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse createWorkloadResources(Workload workload, Map opts) {
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
	ServiceResponse startServer(ComputeServer computeServer) {
		return ServiceResponse.success()
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
		return PROVISION_PROVIDER_CODE
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
	Collection<ComputeServerInterfaceType> getComputeServerInterfaceTypes() {
		[
				new ComputeServerInterfaceType(
						name: "VLAN",
						code: CSI_VLAN_CODE,
						displayOrder: 1,
						defaultType: false,
						enabled: true,
						vlan: true,
						deleteOnWorkloadRemoval: true,
				),
				new ComputeServerInterfaceType(
						name: "Physical",
						code: CSI_PHYS_CODE,
						displayOrder: 0,
						defaultType: true,
						enabled: true,
						hasChildInterfaces: true,
						childTypes: [
								new ComputeServerInterfaceType(code: CSI_VLAN_CODE)
						]
				),
				new ComputeServerInterfaceType(
						name: "Bond",
						code: CSI_BOND_CODE,
						displayOrder: 2,
						defaultType: false,
						enabled: true,
						bonded: true,
						deleteOnWorkloadRemoval: true,
						hasChildInterfaces: true,
						childTypes: [
								new ComputeServerInterfaceType(code: CSI_VLAN_CODE)
						]
				),
		]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasNetworks() { true }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<VirtualImageType> getVirtualImageTypes() { [ new VirtualImageType(code: 'iso', name: 'ISO') ] }

	/**
	 * {@inheritDoc}
	 *
	 * Here we've gotta pick an instance from our set of available servers in our cloud that are in an
	 * 'available' state.
	 */
	@Override
	Collection<ComputeServer> getInstanceServers(Instance instance, ProvisionType provisionType, Map opts) {
		String lock = null
		try {
			lock = morpheus.acquireLock(SERVER_LOCK_ID, [ttl: 30000l, timeout: 3000l]).blockingGet()
			def cloudID = opts.zoneId
			def cloud = morpheus.services.cloud.get(cloudID as Long)
			def serverCount = instance.layout.serverCount
			def availableServers = morpheus.services.computeServer.list(new DataQuery().withFilters(
					new DataFilter("zone.id", cloudID),
					new DataFilter("status", "available"),
			))

			if (!availableServers.size()) {
				throw new RuntimeException("No available servers found in cloud")
			}

			if (availableServers.size() < serverCount) {
				throw new RuntimeException("Capacity or Availability Limit Reached for Cloud: ${cloud.name}.")
			}

			// grab our servers and mark them as 'provisioning' to ensure no one else can take them in the meantime.
			def servers = availableServers.take(serverCount)
			servers.each { it ->
				it.status = "provisioning"
			}
			morpheus.services.computeServer.bulkSave(servers)

			return servers as Collection<ComputeServer>
		} finally {
			if (lock) {
				morpheus.releaseLock(SERVER_LOCK_ID, [lock: lock]).blockingGet()
			}
		}
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
	 */
	@Override
	Boolean supportsAutoDatastore() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean canCustomizeDataVolumes() {
		return true
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean canCustomizeRootVolume() {
		return true
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean canAddVolumes() {
		return true
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean rootVolumeSizeKnown() {
		return false
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean disableRootDatastore() {
		return true
	}

	@Override
	ServiceResponse<PrepareResizeV2WorkloadResponse> prepareResizeWorkload(Instance instance, Workload workload, ResizeV2Request resizeRequest, Map opts) {
		log.info("prepare resize called")
		return ServiceResponse.success(new PrepareResizeV2WorkloadResponse())
	}
/**
 * {@inheritDoc}
 */
	@Override
	ServiceResponse<ResizeV2WorkloadResponse> resizeWorkload(Instance instance, Workload workload, ResizeV2Request resizeRequest, Map opts) {
		return resizeServer(workload.server, resizeRequest, opts)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ValidateResizeV2WorkloadResponse> validateResizeWorkload(Instance instance, Workload workload, ResizeV2Request resizeRequest, Map opts) {
		log.info("validate resize called")
		return ServiceResponse.success(new ValidateResizeV2WorkloadResponse(allowed: true, hotResize: false))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean canReconfigureNetwork() { true }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean hasInstanceSnapshots() {
		true
	}

	@Override
	ServiceResponse createSnapshot(ComputeServer server, Map opts) {
		log.info("Create snapshot request")
		def resp = context.services.storage.datastoreType.createSnapshot(server, false, opts.forExport as Boolean)
		if (resp.success) {
			def snapshot = resp.data
			snapshot = snapshot.tap {
				it.name = opts.snapshotName
				it.description = opts.description
				it.externalId = "${server.externalId}-${new Date().time}"
				it.cloud =  server.cloud
				it.server = server
				it.account = server.account
				it.currentlyActive = true
			}
			def files = snapshot.snapshotFiles
			def createdSnapshot = context.services.snapshot.create(snapshot)

			files.each {
				it.snapshot = createdSnapshot
				context.services.snapshot.file.create(it)
			}
		}
		return resp
	}

	@Override
	ServiceResponse deleteSnapshots(ComputeServer server, Map opts) {
		log.info("Delete snapshots request")
		def snapshots = server.snapshots.collect { context.services.snapshot.get(it.id) }
		snapshots.each {
			context.services.storage.datastoreType.removeSnapshot(server, it)
		}
		return ServiceResponse.success()
	}

	@Override
	ServiceResponse revertSnapshot(ComputeServer server, Snapshot snapshot, Map opts) {
		log.info("Revert snapshot request")
		// shut down servers
		stopServer(server)
		context.services.storage.datastoreType.revertSnapshot(server, snapshot)
		snapshot.currentlyActive = true
		context.services.snapshot.save(snapshot)
		startServer(server)
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse validateHost(ComputeServer server, Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<PrepareHostResponse> prepareHost(ComputeServer server, HostRequest hostRequest, Map opts) {
		return ServiceResponse.success(new PrepareHostResponse(
				computeServer: server,
				options: opts
		))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> runHost(ComputeServer server, HostRequest hostRequest, Map opts) {
		server.resourcePool = context.async.cloud.pool.find(new DataQuery().withFilters(
				new DataFilter('id', server.configMap.resourcePoolId),
		)).blockingGet()

		if (server.name.contains("Plan2")) {
			def plan = context.services.servicePlan.find(new DataQuery().withFilter('code', 'omega.baremetal.any2'))
			server.plan = plan
		} else {
			def plan = context.services.servicePlan.find(new DataQuery().withFilter('code', 'omega.baremetal.any'))
			server.plan = plan
		}

		morpheus.services.computeServer.save(server)
		return ServiceResponse.success(new ProvisionResponse(installAgent: false, noAgent: true))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> waitForHost(ComputeServer server) {
		return ServiceResponse.success(new ProvisionResponse(skipNetworkWait: true, installAgent: false, noAgent: true))
	}

	/**
	 * {@inheritDoc}
	 *
	 * We need to pretend we have physical hardware on this server.
	 */
	@Override
	ServiceResponse finalizeHost(ComputeServer server) {
		if (server.configMap?.preProvisioned) {
			server.status = 'provisioned'
			server.preProvisioned = true
		} else {
			server.status = 'available'
		}
		// Add wwpns so we can interact with alletra plugin for FC
		server.setConfigProperty("wwpns", [
				'BE:EF:CA:FE:' + (0..3).collect {
					String.format("%02X", new Random().nextInt(256))
				}.join(":")
		])

		// Add iqns so we can interact with alletra plugin for iscsi
		server.setConfigProperty("iqns", [
				"iqn.2016-04.com.hpe:${server.name}-${server.id}".toString(),
		])

		if (server.configMap?.consoleHost) {
			server.consoleType = 'ilo'
			server.consoleHost = server.configMap?.consoleHost
			server.consoleUsername = server.configMap?.consoleUsername
			server.consolePassword = server.configMap?.consolePassword
		}
		server.plan = context.services.servicePlan.find(new DataQuery().withFilter('code', 'omega.baremetal.any'))
		context.services.computeServer.save(server)

		def netInterfaces = []
		def numNics = Long.valueOf(server.configMap.numNics)
		numNics.times { idx ->
			def prefix = "ca:fe:fe" // Common prefix for generated MACs
			def suffix = (0..2).collect {
				String.format("%02x", new Random().nextInt(256))
			}.join(":")

			def syntheticMacaddress = "${prefix}:${suffix}"
			ComputeServerInterface nic = new ComputeServerInterface(
					name: "eth${idx}",
					type: new ComputeServerInterfaceType(code: BaremetalProvisionProvider.CSI_PHYS_CODE),
					macAddress: syntheticMacaddress,
					externalId: syntheticMacaddress,
					dhcp: false,
					primaryInterface: false,
					ipMode: 'static',
			)
			def syntheticIpAddr= (0..3).collect {
				String.format("%d", new Random().nextInt(256))
			}.join(".")
			nic.addresses << new NetAddress(address: syntheticIpAddr, type: NetAddress.AddressType.IPV4)

			netInterfaces << nic
		}

		context.async.computeServer.computeServerInterface.create(netInterfaces, server).blockingGet()
		server = context.services.computeServer.get(server.id)

		// Pretend we discovered some devices on this server.
		def discoveredDevices = [
				[ name: "Generic USB", vendorId: 1, productId: 1, type: 'usb_device', domain: 0000, bus: 00, device: 14, function: 0, iommuGroup: 0 ], // generic usb
				[ name: "Generic PCI", vendorId: 1, productId: 1, type: 'pci', domain: 0000, bus: 0x0e, device: 01, function: 0, iommuGroup: 0 ], // generic pci
				[ name: "Nvidia Generic GPU", vendorId: 4318, type: 'pci', domain: 0000, bus: 0x0e, device: 02, function: 0, iommuGroup: 0 ], // Nvidia Generic GPU
				[ name: "Nvidia GeForce RTX 4090", vendorId: 4318, productId: 9860, type: 'pci', domain: 0000, bus: 0x0e, device: 13, function: 0, iommuGroup: 0 ], // Nvidia GeForce RTX 4090
				[ name: "Omega Baremetal GPU", vendorId: 1337, productId: 1337, type: 'pci', domain: 0000, bus: 0x0e, device: 14, function: 0, iommuGroup: 0 ], // Fake device type that doesn't really exist
		]

		for (def discoveredDevice in discoveredDevices) {
			// check if we know the exact type of the device by vendorId and productId
			def type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilters(
					new DataFilter('vendorId', discoveredDevice.vendorId),
					new DataFilter('productId', discoveredDevice.productId),
			))

			if(!type) {
				// well maybe we know who made it at least and we can pick a generic type for that vendor
				type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilters(
						new DataFilter('vendorId', discoveredDevice.vendorId),
						new NullDataFilter<>('productId'), // we want the generic type for this vendor, there shouldn't be a productId
						new DataFilter('bus_type', discoveredDevice.type), // pci or usb_device
				))
			}

			if(!type) {
				// if we don't know the type by vendorId and productId but we know what kind of device it is, pick the generic
				if (discoveredDevice.type == 'usb_device') {
					type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilter(
							new DataFilter('code', 'usb'),
					))
				} else if (discoveredDevice.type == 'pci') {
					type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilter(
							new DataFilter('code', 'pci'),
					))
				}
			}

			if (!type) {
				log.warn("Could not find a compute device type for vendorId: ${discoveredDevice.vendorId}, productId: ${discoveredDevice.productId}, type: ${discoveredDevice.type}")
				return
			}

			def computeDevice = new ComputeDevice(
					name: discoveredDevice.name,
					vendorId: discoveredDevice.vendorId,
					productId: discoveredDevice.productId,
					type: type,
					domainId: discoveredDevice.domain,
					bus: discoveredDevice.bus,
					device: discoveredDevice.device,
					functionId: discoveredDevice.function,
					iommuGroup: discoveredDevice.iommuGroup,
					server: server, // you must have a server attached.
			)
			context.services.computeServer.computeDevice.create(computeDevice)
		}

		// Create a couple of synthetic disk StorageVolumes for this host
		def diskType = context.services.storage.volume.storageVolumeType.find(
			new DataQuery().withFilter("code", "standard")
		)

		def numDisks = Long.valueOf(server.configMap.numDisks)

		def disks = []
		numDisks.times { idx ->
			def deviceLetter = (char)((int)'b' + idx)
			disks << new StorageVolume(
					name: "disk${idx}",
					type: diskType,
					maxStorage: (1 + idx) * 100L * 1024L * 1024L * 1024L, // 100GB, 100GB, 200GB, etc.
					volumeType: "disk",
					displayOrder: idx + 1,
					rootVolume: false,
					deviceName: "/dev/sd${deviceLetter}",
					uniqueId: "${UUID.randomUUID().toString().replace('-','')}",
					externalId: "omega-${UUID.randomUUID().toString().replace('-','')}",

			)
		}
		context.async.storageVolume.create(disks, server).blockingGet()

		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<BeforeConvertToManagedResponse> beforeConvertToManaged(BeforeConvertToManagedRequest beforeConvertToManagedRequest) {
		return ServiceResponse.success(new BeforeConvertToManagedResponse(
				server: beforeConvertToManagedRequest.server,
				opts: [alley: 'oop']
		))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<AfterConvertToManagedResponse> afterConvertToManaged(AfterConvertToManagedRequest afterConvertToManagedRequest) {
		log.info("alley-${afterConvertToManagedRequest.opts?.alley}")
		return ServiceResponse.success(new AfterConvertToManagedResponse(
				instance: afterConvertToManagedRequest.instance,
				workloads: afterConvertToManagedRequest.workloads,
				server: afterConvertToManagedRequest.server,
		))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Boolean supportsAddPreprovisionedServer() {
		return true
	}

	@Override
	ServiceResponse createSnapshot(Instance instance, Map opts) {
		log.info("Create instance snapshot request")
		CreateSnapshotRequest req = new CreateSnapshotRequest(false, opts.forExport as Boolean)
		def resp = context.services.storage.datastoreType.createSnapshot(instance, req)
		if (resp.success) {
			def instanceSnapshot = resp.data
			instanceSnapshot = instanceSnapshot.tap {
				it.name = opts.snapshotName
				it.description = opts.description
				it.externalId = "${instance.externalId}-${new Date().time}"
				it.instance = instance
				it.account = instance.account
				it.currentlyActive = true
			}

			def files = instanceSnapshot.snapshotFiles
			def createdInstanceSnapshot = context.services.snapshot.create(instanceSnapshot)

			files.each {
				it.snapshot = createdInstanceSnapshot
				def createdFile =  context.services.snapshot.file.create(it)
				createdInstanceSnapshot.snapshotFiles << createdFile
			}

			def serverSnapshotsCopy = new ArrayList<>(createdInstanceSnapshot.snapshots)
			serverSnapshotsCopy.each { serverSnapshot ->
				serverSnapshot.tap {
					serverSnapshot.name = "${opts.snapshotName} - Server ${it.server.name}"
					serverSnapshot.description = opts.description
					serverSnapshot.externalId = "${instance.externalId}-${it.server.externalId}-${new Date().time}"
					serverSnapshot.parentSnapshot = createdInstanceSnapshot
					serverSnapshot.account = instance.account
					serverSnapshot.currentlyActive = true
				}

				files = serverSnapshot.snapshotFiles
				def createdServerSnapshot = context.services.snapshot.create(serverSnapshot)
				files.each {
					it.snapshot = createdServerSnapshot
					createdServerSnapshot.snapshotFiles << context.services.snapshot.file.create(it)
				}

				createdInstanceSnapshot.snapshots << createdServerSnapshot
			}

			createdInstanceSnapshot = context.services.snapshot.save(createdInstanceSnapshot)


			instance.snapshots << createdInstanceSnapshot
			context.services.instance.save(instance)
		}

		resp
	}

	@Override
	ServiceResponse deleteSnapshots(Instance instance, Map opts) {
		log.info("Delete instance snapshots request")
		def snapshots = instance.snapshots.collect { context.services.snapshot.get(it.id) }
		snapshots.each {
			context.services.storage.datastoreType.removeSnapshot(instance, it)
		}
		return ServiceResponse.success()
	}

	@Override
	ServiceResponse deleteSnapshot(Snapshot snapshot, Map opts) {
		log.info("Delete snapshot request")
		if ((!opts.serverId) && (!opts.instanceId)) {
			return ServiceResponse.error("Unable to delete snapshot, no server id or instance id provided")
		}

		if (opts.instanceId) {
			Instance instance = context.services.instance.get(Long.valueOf(opts.instanceId))
			context.services.storage.datastoreType.removeSnapshot(instance, snapshot)
		} else if (opts.serverId) {
			ComputeServer server = context.services.computeServer.get(Long.valueOf(opts.serverId))
			context.services.storage.datastoreType.removeSnapshot(server, snapshot)
		}

		return ServiceResponse.success()
	}

	@Override
	ServiceResponse revertSnapshot(Instance instance, Snapshot snapshot, Map opts) {
		log.info("Revert instance snapshot request")
		// Stop each server in the instance
		instance.containers.each { it ->
			stopServer(it.server)
		}

		context.services.storage.datastoreType.revertSnapshot(instance, snapshot)
		snapshot.currentlyActive = true
		context.services.snapshot.save(snapshot)
		instance.containers.each { it ->
			startServer(it.server)
		}
		return ServiceResponse.success()
	}

	@Override
	ServiceResponse<ValidateResizeV2WorkloadResponse> validateResizeServer(ComputeServer server, ResizeV2Request resizeRequest, Map opts) {
		return ServiceResponse.success(new ValidateResizeV2WorkloadResponse(allowed:true, hotResize: true))
	}

	@Override
	ServiceResponse<PrepareResizeV2WorkloadResponse> prepareResizeServer(ComputeServer server, ResizeV2Request resizeRequest, Map opts) {
		return ServiceResponse.success(new PrepareResizeV2WorkloadResponse())
	}

	@Override
	ServiceResponse<ResizeV2WorkloadResponse> resizeServer(ComputeServer server, ResizeV2Request resizeRequest, Map opts) {
		log.info("resize called")
		resizeRequest.interfacesAdd.each {
			def csi = it.existingModel
			if (it.updateProps?.networkConfiguration?.ipAddress) {
				csi.addresses << new NetAddress(NetAddress.AddressType.IPV4, it.updateProps?.networkConfiguration.ipAddress)
			}
			csi.vlanId = it.updateProps?.networkConfiguration?.vlan
			context.services.computeServer.computeServerInterface.save([csi])
		}

		resizeRequest.interfacesUpdate.each {
			def csi = it.existingModel
			if (it.updateProps?.networkConfiguration?.ipAddress) {
				csi.addresses << new NetAddress(NetAddress.AddressType.IPV4, it.updateProps?.networkConfiguration?.ipAddress)
			}
			if (!it.updateProps?.network) {
				csi.network = null
			}
			csi.vlanId = it.updateProps?.networkConfiguration?.vlan
			context.services.computeServer.computeServerInterface.save([csi])
		}

		resizeRequest.volumesUpdate.each {
			def volume = it.existingModel
			if (volume.maxStorage != it.updateProps.maxStorage && volume.datastore) {
				volume.maxStorage = it.updateProps.maxStorage
				volume = context.services.storage.datastoreType.resizeVolume(volume, server, true)
				context.services.storage.volume.save(volume)
			}
		}

		return ServiceResponse.success(new ResizeV2WorkloadResponse(
				preserveVolumes: true
		))
	}
//
//	@Override
//	Boolean hasComputeZonePools() {
//		return true
//	}
//	/**
//	 * Indicates if a ComputeZonePool is required during provisioning
//	 * @return Boolean
//	 */
//	@Override
//	Boolean computeZonePoolRequired() {
//		return true
//	}


	@Override
	ServiceResponse validateInstance(Instance instance, Map opts) {
		def sizeMultiplier = opts.layoutSize as Integer ?: 1
		def minServerCount = instance.layout.serverCount
		def provisionCount = opts.provisionCount as Integer ?: minServerCount * sizeMultiplier
//		if (opts.config.hosts.length != provisionCount) {
//			def errMsg = "Invalid number of bare metal hosts selected, instance scale factor requires ${provisionCount} host(s)"
//			return ServiceResponse.error(errMsg,['hosts':errMsg])
//		}

		ServiceResponse.success()
	}

	@Override
	ServiceResponse<ProvisionResponse> updateInstance(Instance instance, InstanceRequest instanceRequest, Map opts) {
		ServiceResponse.success(new ProvisionResponse())
	}

	@Override
	ServiceResponse<PrepareInstanceResponse> prepareInstance(Instance instance, InstanceRequest instanceRequest, Map opts) {
		ServiceResponse.success(new ProvisionResponse())
	}

	@Override
	ServiceResponse<ProvisionResponse> runInstance(Instance instance, InstanceRequest instanceRequest, Map opts) {
		ServiceResponse.success(new ProvisionResponse())
	}

	@Override
	ServiceResponse destroyInstance(Instance instance, Map opts) {
		ServiceResponse.success(new ProvisionResponse())
	}

	/**
	 * Generate support bundle contents for this provision provider.
	 * This method is called when a support bundle is being generated for a server
	 * and allows the provider to add custom diagnostic information.
	 *
	 * @param server The server to generate support bundle contents for
	 * @param request The request containing the target directory and resource info
	 * @return ServiceResponse indicating success or failure
	 */
	@Override
	ServiceResponse generateSupportBundleContents(ComputeServer server, GenerateSupportBundleContentsRequest request) {
		log.info("Generating support bundle contents for Baremetal server: ${server.name}")

		try {
			// Add server-specific data to resourceInfo that looks like it came from external IPMI/BMC
			request.resourceInfo.putAll([
				bmcFirmwareVersion: "2.${new Random().nextInt(10)}.${new Random().nextInt(100)}",
				biosVersion: "U${new Random().nextInt(50) + 10}",
				ipmiFirmware: "v${new Random().nextInt(5) + 1}.${new Random().nextInt(99)}",
				hardwareModel: ['ProLiant DL380', 'PowerEdge R740', 'SuperServer 6029P'][new Random().nextInt(3)],
				cpuModel: "Intel Xeon Gold ${5100 + new Random().nextInt(300)}",
				cpuSockets: server.maxCores ? (server.maxCores / 16).toInteger() : 2,
				coresPerSocket: 16,
				memoryModules: server.maxMemory ? ((server.maxMemory / 1024 / 1024 / 1024) / 32).toInteger() : 4,
				memoryType: 'DDR4-2933',
				diskCount: new Random().nextInt(4) + 2,
				nicCount: new Random().nextInt(4) + 2,
				powerSupplies: 2,
				fanModules: new Random().nextInt(4) + 4,
				cpuTemp: new Random().nextInt(30) + 40,
				systemTemp: new Random().nextInt(20) + 30,
				powerConsumption: new Random().nextInt(300) + 200,
				selEntries: new Random().nextInt(50),
				lastBootTime: new Date(System.currentTimeMillis() - (new Random().nextInt(7200) * 60000)).format('yyyy-MM-dd HH:mm:ss'),
				bootDevice: 'Disk',
				biosMode: 'UEFI',
				secureBoot: new Random().nextBoolean(),
				tpmPresent: true,
				raidController: 'Smart Array P408i-a',
				raidConfig: 'RAID1',
				networkBootStatus: 'Enabled',
				ipmiAccessible: true,
				redfishEndpoint: "https://${server.internalIp ?: '10.0.0.100'}/redfish/v1",
				consoleAvailable: true
			])

			// Write fake logs that look like they were fetched from IPMI/BMC
			def ipmiLogsFile = request.contentsDir['ipmi-bmc-logs.txt']
			ipmiLogsFile.text = """[2026-02-14 14:22:10] INFO  - IPMI session established with host: ${server.name}
[2026-02-14 14:22:11] DEBUG - BMC firmware version: 2.${new Random().nextInt(10)}.${new Random().nextInt(100)}
[2026-02-14 14:22:12] INFO  - Power state: ${server.powerState ?: 'on'}
[2026-02-14 14:22:13] DEBUG - Reading system event log (SEL)...
[2026-02-14 14:22:14] INFO  - SEL entries: ${new Random().nextInt(50)}
[2026-02-14 14:22:15] DEBUG - Sensor readings retrieved successfully
[2026-02-14 14:22:16] INFO  - CPU Temperature: ${new Random().nextInt(30) + 40}°C
[2026-02-14 14:22:17] INFO  - System Temperature: ${new Random().nextInt(20) + 30}°C
[2026-02-14 14:22:18] DEBUG - Fan speeds nominal (${new Random().nextInt(1000) + 2000} RPM)
[2026-02-14 14:22:19] INFO  - Power consumption: ${new Random().nextInt(300) + 200}W
[2026-02-14 14:22:20] DEBUG - Voltage levels within spec
[2026-02-14 14:22:21] INFO  - Memory status: ${server.maxMemory ? (server.maxMemory / 1024 / 1024 / 1024).toLong() : 'Unknown'} GB installed, ECC enabled
[2026-02-14 14:22:22] DEBUG - Storage controller status: OK
[2026-02-14 14:22:23] INFO  - Network interfaces: ${new Random().nextInt(4) + 1} active
[2026-02-14 14:22:24] DEBUG - BIOS POST completed successfully
[2026-02-14 14:22:25] INFO  - Overall system health: OK
[2026-02-14 14:22:26] INFO  - IPMI session closed
"""

			def provisioningHistoryFile = request.contentsDir['provisioning-history.log']
			provisioningHistoryFile.text = """Baremetal Server Provisioning History
======================================
Server: ${server.name}
Server ID: ${server.id}
External ID: ${server.externalId ?: 'N/A'}
Timestamp: ${new Date().format('yyyy-MM-dd HH:mm:ss')}

Hardware Configuration:
- CPU Cores: ${server.maxCores ?: 'Unknown'}
- Memory: ${server.maxMemory ? (server.maxMemory / 1024 / 1024 / 1024).toLong() : 'Unknown'} GB
- Storage: ${server.maxStorage ? (server.maxStorage / 1024 / 1024 / 1024).toLong() : 'Unknown'} GB
- Plan: ${server.plan?.name ?: 'N/A'}

Network Configuration:
- Internal IP: ${server.internalIp ?: 'Not assigned'}
- External IP: ${server.externalIp ?: 'Not assigned'}
- MAC Address: ${server.macAddress ?: 'Unknown'}
- VLAN: ${new Random().nextInt(100) + 10}

Provisioning Timeline:
- Server Added: ${server.dateCreated?.format('yyyy-MM-dd HH:mm:ss') ?: 'Unknown'}
- Last Updated: ${server.lastUpdated?.format('yyyy-MM-dd HH:mm:ss') ?: 'Unknown'}
- PXE Boot: SUCCESS
- OS Installation: COMPLETED
- Agent Installation: ${server.agentInstalled ? 'SUCCESS' : 'PENDING'}
- Post-Install Scripts: COMPLETED
- Health Check: PASSED

Resource Pool:
- Pool Name: ${server.resourcePool?.name ?: 'N/A'}
- Pool ID: ${server.resourcePool?.id ?: 'N/A'}
- Cloud: ${server.cloud?.name ?: 'N/A'}

Current Status:
- Power State: ${server.powerState ?: 'unknown'}
- Server Status: ${server.status ?: 'unknown'}
- Connection Status: ${server.agentInstalled ? 'CONNECTED' : 'PENDING'}
- Uptime: ${new Random().nextInt(7200)} minutes

Recent Operations:
- Last Power Action: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
- Last Sync: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
- Last Health Check: ${new Date().format('yyyy-MM-dd HH:mm:ss')}

Status: OPERATIONAL
"""

			log.info("Successfully generated support bundle contents for Baremetal server: ${server.name}")
			return ServiceResponse.success()

		} catch (Exception e) {
			log.error("Error generating support bundle contents for Baremetal server ${server.name}: ${e.message}", e)
			return ServiceResponse.error("Failed to generate support bundle contents: ${e.message}")
		}
	}
}