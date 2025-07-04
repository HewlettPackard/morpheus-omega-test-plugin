package com.morpheusdata.omega.baremetal

import com.morpheusdata.core.AbstractProvisionProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.ProvisionInstanceServers
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.providers.WorkloadProvisionProvider
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerInterfaceType
import com.morpheusdata.model.Icon
import com.morpheusdata.model.Instance
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ProvisionType
import com.morpheusdata.model.ServicePlan
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.model.Workload
import com.morpheusdata.model.provisioning.RemoveWorkloadRequest
import com.morpheusdata.model.provisioning.WorkloadRequest
import com.morpheusdata.response.PrepareWorkloadResponse
import com.morpheusdata.response.ProvisionResponse
import com.morpheusdata.response.ServiceResponse

/**
 * Provision provider for provisioning baremetal servers. This picks from the pool of servers in an 'available'
 * state.
 */
class BaremetalProvisionProvider extends AbstractProvisionProvider implements WorkloadProvisionProvider, ProvisionInstanceServers {
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
	Collection<OptionType> getOptionTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<OptionType> getNodeOptionTypes() { [] }


	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<StorageVolumeType> getRootVolumeStorageTypes() { [] }

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
		def rootVol  = workload.server.volumes.find { it.rootVolume }
		if (rootVol) {
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
	Boolean requiresVirtualImage() { false }

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

}
