/*
* Copyright 2022 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.morpheusdata.omega.storage

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.StorageVolumeResourceViewUIFacet
import com.morpheusdata.model.*
import com.morpheusdata.views.Renderer
import groovy.util.logging.Slf4j

/**
 * Test implementation of StorageVolumeResourceViewUIFacet to demonstrate
 * how plugins can inject custom information into storage volume detail views.
 *
 * This example adds sample custom details like encryption status, backup policy,
 * and performance tier information to storage volume info sections.
 */
@Slf4j
class OmegaStorageVolumeDetailProvider implements StorageVolumeResourceViewUIFacet {

	Plugin plugin
	MorpheusContext morpheusContext

	OmegaStorageVolumeDetailProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	/**
	 * Returns the Morpheus Context for interacting with data and API's
	 */
	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

	/**
	 * Returns the Plugin Instance this provider belongs to
	 */
	@Override
	Plugin getPlugin() {
		return plugin
	}

	/**
	 * A unique shortcode used for referencing the provider. Make sure this is going to be unique as any conflicts
	 * will cause the plugin to not load.
	 * @return short code string that should be unique across all other plugin implementations.
	 */
	@Override
	String getCode() {
		return 'omega-storage-volume-details'
	}

	/**
	 * Provides the provider name for reference when adding to the Morpheus Orchestrator
	 * NOTE: This may be useful to set as an i18n key for UI reference and localization support.
	 *
	 * @return either an English name of a Provider or an i18n based key that can be scanned for in a properties file.
	 */
	@Override
	String getName() {
		return 'Omega Storage Volume Details Provider'
	}

	/**
	 * Returns the renderer for this provider. Since we only return data (not render templates),
	 * we return null as this is not needed for ResourceViewUIFacet implementations.
	 * @return null as no rendering is required
	 */
	@Override
	Renderer<?> getRenderer() {
		return null
	}

	/**
	 * Get custom storage volume details to display in the Info section
	 * @param storageVolume The StorageVolume instance to get details for
	 * @return List of DetailEntry objects to display in the Info section
	 */
	@Override
	List<DetailEntry> getDetailViewInfo(StorageVolume storageVolume) {
		log.debug("Getting detail view info for storage volume: ${storageVolume?.name}")

		List<DetailEntry> details = []

		try {
			// Example custom details that a plugin might want to show
			details.add(new DetailEntry(
				"omega.storage.encryption.label",
				"Encryption Status",
				determineEncryptionStatus(storageVolume)
			))

			details.add(new DetailEntry(
				"omega.storage.backup.policy.label",
				"Backup Policy",
				determineBackupPolicy(storageVolume)
			))

			details.add(new DetailEntry(
				"omega.storage.performance.tier.label",
				"Performance Tier",
				determinePerformanceTier(storageVolume)
			))

			details.add(new DetailEntry(
				"omega.storage.last.snapshot.label",
				"Last Snapshot",
				getLastSnapshotDate(storageVolume)
			))

			// Show some actual properties from the storage volume
			if (storageVolume.getExternalId()) {
				details.add(new DetailEntry(
					"omega.storage.external.id.label",
					"Omega External ID",
					storageVolume.getExternalId()
				))
			}

			// Show pool name if available
			if (storageVolume.getPoolName()) {
				details.add(new DetailEntry(
					"omega.storage.pool.name.label",
					"Storage Pool",
					storageVolume.getPoolName()
				))
			}

			// Show region code if available
			if (storageVolume.getRegionCode()) {
				details.add(new DetailEntry(
					"omega.storage.region.label",
					"Region",
					storageVolume.getRegionCode()
				))
			}

		} catch (Exception e) {
			log.error("Error getting storage volume details: ${e.message}", e)
		}

		return details
	}

	/**
	 * Determine when these details should be displayed
	 * @param storageVolume The StorageVolume instance
	 * @param user Current User details
	 * @param account Account details
	 * @return whether the details should be displayed
	 */
	@Override
	Boolean show(StorageVolume storageVolume, User user, Account account) {
		// Only show for storage volumes created by the Omega storage provider
		// Check if the storage volume type belongs to our Omega plugin
		if (storageVolume?.type?.code?.startsWith('omega.sstp')) {
			log.debug("Showing Omega storage details for volume: ${storageVolume.name} with type: ${storageVolume.type.code}")
			return true
		}

		log.debug("Not showing Omega storage details for volume: ${storageVolume?.name} with type: ${storageVolume?.type?.code} (not an Omega volume)")
		return false
	}

	// Helper methods to simulate getting custom information about storage volumes

	private String determineEncryptionStatus(StorageVolume storageVolume) {
		// In a real implementation, this might call an external API
		// or check storage volume properties
		if (storageVolume?.maxStorage > 50000000000) { // 50GB+
			return "AES-256 Enabled"
		} else {
			return "Standard Encryption"
		}
	}

	private String determineBackupPolicy(StorageVolume storageVolume) {
		// Example logic for determining backup policy
		if (storageVolume?.rootVolume) {
			return "Daily (Root Volume)"
		} else {
			return "Weekly"
		}
	}

	private String determinePerformanceTier(StorageVolume storageVolume) {
		// Example logic based on volume size
		if (storageVolume?.maxStorage > 100000000000) { // 100GB+
			return "High Performance SSD"
		} else if (storageVolume?.maxStorage > 10000000000) { // 10GB+
			return "Standard SSD"
		} else {
			return "Standard HDD"
		}
	}

	private String getLastSnapshotDate(StorageVolume storageVolume) {
		// In a real implementation, this would query for actual snapshots
		// For testing, return a mock date
		return "2025-09-28 14:30:00"
	}
}
