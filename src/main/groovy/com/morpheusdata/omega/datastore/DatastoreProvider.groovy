package com.morpheusdata.omega.datastore

import com.bertramlabs.plugins.karman.CloudFileInterface
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.DatastoreTypeProvider
import com.morpheusdata.model.*
import com.morpheusdata.omega.storageserver.StorageServerProvider
import com.morpheusdata.response.ServiceResponse
import groovy.util.logging.Slf4j

import java.time.Instant

/**
 * An example datastore provider
 */
@Slf4j
class DatastoreProvider implements DatastoreTypeProvider, DatastoreTypeProvider.SnapshotFacet.SnapshotServerFacet {
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
}
