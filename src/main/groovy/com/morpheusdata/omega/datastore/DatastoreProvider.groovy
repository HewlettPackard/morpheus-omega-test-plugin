package com.morpheusdata.omega.datastore

import com.bertramlabs.plugins.karman.CloudFileInterface
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.providers.DatastoreTypeProvider
import com.morpheusdata.model.*
import com.morpheusdata.omega.storageserver.StorageServerProvider
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.request.CreateSnapshotRequest
import groovy.util.logging.Slf4j

import java.time.Instant

/**
 * An example datastore provider
 */
@Slf4j
class DatastoreProvider implements DatastoreTypeProvider, DatastoreTypeProvider.SnapshotFacet.SnapshotServerFacet, DatastoreTypeProvider.SnapshotFacet.SnapshotInstanceFacet, DatastoreTypeProvider.ComputeProvisionFacet {
	public static final String PROVIDER_CODE = "omega.datastore"

	private final Plugin plugin
	private final MorpheusContext morpheusContext

	DatastoreProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getProvisionTypeCode() {
		return ""
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getStorageProviderCode() { StorageServerProvider.STORAGE_PROVIDER_CODE }

	/**
	 * {@inheritDoc}
	 */
	@Override
	List<OptionType> getOptionTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean getCreatable() { true }

	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean getEditable() { true }

	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean getRemovable() { true }

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse removeVolume(StorageVolume volume, ComputeServer server, boolean removeSnapshots, boolean force) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<StorageVolume> createVolume(StorageVolume volume, ComputeServer server) {
		return ServiceResponse.success(volume)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<StorageVolume> cloneVolume(StorageVolume volume, ComputeServer server, StorageVolume sourceVolume) {
		return ServiceResponse.success(volume)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<StorageVolume> cloneVolume(StorageVolume volume, ComputeServer server, VirtualImage virtualImage, CloudFileInterface cloudFile) {
		return ServiceResponse.success(volume)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<StorageVolume> resizeVolume(StorageVolume volume, ComputeServer server, Long newSize) {
		return ServiceResponse.success(volume)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<Datastore> createDatastore(Datastore datastore) {
		return ServiceResponse.success(datastore)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse removeDatastore(Datastore datastore) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	MorpheusContext getMorpheus() { this.morpheusContext }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Plugin getPlugin() { this.plugin }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getCode() { PROVIDER_CODE }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getName() { "Omega Datastore" }

	@Override
	ServiceResponse<Snapshot> createSnapshot(ComputeServer server, Boolean forBackup, Boolean forExport) {
		log.info("Creating a snapshot")
		Snapshot snapshot = new Snapshot()
		snapshot.snapshotFiles = server.volumes.findAll {
			def datastore = morpheusContext.services.cloud.datastore.get(it.datastore.id)
			datastore.datastoreType.code == this.code
		}.collect{
		        new SnapshotFile(
						name: "${server.name}-${Instant.now().toEpochMilli()}",
						volume: it,
				)
		}
		return ServiceResponse.success(snapshot)
	}

	@Override
	ServiceResponse<Snapshot> revertSnapshot(ComputeServer server, Snapshot snapshot) {
		log.info("Reverting a snapshot")
		return ServiceResponse.success(snapshot)
	}

	@Override
	ServiceResponse removeSnapshot(ComputeServer server, Snapshot snapshot) {
		log.info("Removing a snapshot")
		return ServiceResponse.success()
	}

	@Override
	ServiceResponse<StorageVolume> prepareServerForVolume(ComputeServer server, StorageVolume volume) {
		log.info("Preparing server for volume")
		return ServiceResponse.success(volume)
	}

	@Override
	ServiceResponse<StorageVolume> releaseVolumeFromServer(ComputeServer server, StorageVolume volume) {
		log.info("Releasing volume from server")
		return ServiceResponse.success(volume)
	}

	@Override
	List<StorageVolumeType> getVolumeTypes() {
		return [morpheusContext.services.storage.volume.storageVolumeType.find(new DataQuery().withFilter('code', 'omega.sstp.block'))]
	}

    @Override
    ServiceResponse<Snapshot> createSnapshot(Instance instance, CreateSnapshotRequest req) {
        log.info("Creating an instance snapshot")
        // Main instance snapshot
        Snapshot instanceSnapshot = new Snapshot([name: "${instance.name}-${Instant.now().toEpochMilli()}"])
        instanceSnapshot.instance = instance

        instance.containers.each { container ->
            def server =  morpheusContext.services.workload.get(container.id).server
            // Create a snapshot for the server
            Snapshot serverSnapshot = new Snapshot([name: "${server.name}-${Instant.now().toEpochMilli()}"])
            serverSnapshot.server = server
			serverSnapshot.cloud = server.cloud
            // Add a snapshot file for each data volume of the server
            serverSnapshot.snapshotFiles = server.volumes.findAll { volume ->
				if (volume.name!="root") {
					def datastore = morpheusContext.services.cloud.datastore.get(volume.datastore.id)
					datastore.datastoreType.code == this.code
				}
            }.collect { volume ->
				new SnapshotFile(
						name: "${server.name}-${volume.name}-${Instant.now().toEpochMilli()}",
						volume: volume
				)
            }

			// Using root volumes as shared volumes only for testing purposes
			instanceSnapshot.snapshotFiles = server.volumes.findAll { volume ->
				if (volume.name=="root") {
					def datastore = morpheusContext.services.cloud.datastore.get(volume.datastore.id)
					datastore.datastoreType.code == this.code
				}
			}.collect { volume ->
				new SnapshotFile(
						name: "${instance.name}-${volume.name}-${Instant.now().toEpochMilli()}",
						volume: volume
				)
			}

			instanceSnapshot.snapshots << serverSnapshot
        }
        return ServiceResponse.success(instanceSnapshot)
    }

	@Override
	ServiceResponse<Snapshot> revertSnapshot(Instance instance, Snapshot snapshot) {
		log.info("Reverting an instance snapshot")
		return ServiceResponse.success(snapshot)
	}

	@Override
	ServiceResponse removeSnapshot(Instance instance, Snapshot snapshot) {
		log.info("Removing an instance snapshot")
		return ServiceResponse.success()
	}
}
