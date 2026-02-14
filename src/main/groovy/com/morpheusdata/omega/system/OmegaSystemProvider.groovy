package com.morpheusdata.omega.system

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.SystemProvider
import com.morpheusdata.model.GenerateSupportBundleContentsRequest
import com.morpheusdata.model.Icon
import com.morpheusdata.model.system.System
import com.morpheusdata.model.system.SystemComponentType
import com.morpheusdata.model.system.SystemType
import com.morpheusdata.model.system.SystemTypeLayout
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.response.ServiceResponse

class OmegaSystemProvider implements SystemProvider, SystemProvider.SystemSupportBundleFacet {

	public static final String SYSTEM_PROVIDER_CODE = 'omega.system'

	protected MorpheusContext morpheusContext
	protected Plugin plugin
	protected final LogWrapper log = LogWrapper.instance

	OmegaSystemProvider(Plugin plugin, MorpheusContext morpheusContext) {
		super()
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	@Override
	String getDescription() {
		return 'This is a custom system provider for Morpheus Omega Test Plugin.'
	}

	@Override
	Icon getIcon() {
		return new Icon(path: "morpheus.svg", darkPath: "morpheus.svg")
	}

	@Override
	Collection<SystemComponentType> getSystemComponentTypes() {
		return []
	}

	@Override
	Collection<SystemType> getSystemTypes() {
		def systemType = new SystemType()
		systemType.code = 'omega.system'
		systemType.name = 'Omega Test System'
		systemType.description = 'Test system type for Omega plugin'
		systemType.active = true
		return [systemType]
	}

	@Override
	Collection<SystemTypeLayout> getSystemTypeLayouts() {
		def systemType = new SystemType()
		systemType.code = 'omega.system'

		def layout = new SystemTypeLayout()
		layout.code = 'omega.system.layout'
		layout.name = 'Omega System Layout'
		layout.description = 'Default layout for Omega test systems'
		layout.systemType = systemType
		return [layout]
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
		return SYSTEM_PROVIDER_CODE
	}

	@Override
	String getName() {
		return "System Test Provider"
	}

	/**
	 * Generate support bundle contents for this system.
	 *
	 * @param system The system to generate support bundle contents for
	 * @param request The request containing the target directory and resource info
	 * @return ServiceResponse indicating success or failure
	 */
	@Override
	ServiceResponse generateSupportBundleContents(System system, GenerateSupportBundleContentsRequest request) {
		log.info("Generating support bundle contents for Omega system: ${system.name}")

		try {
			// Add provider-specific data to resourceInfo that looks like it came from external monitoring API
			request.resourceInfo.putAll([
				monitoringApiVersion: "v${new Random().nextInt(3) + 2}.${new Random().nextInt(10)}",
				systemVersion: "${new Random().nextInt(5) + 1}.${new Random().nextInt(10)}.${new Random().nextInt(100)}",
				componentCount: system.components?.size() ?: new Random().nextInt(20),
				healthStatus: 'OPERATIONAL',
				uptimeHours: Math.abs(new Random().nextInt(1000)),
				cacheHitRatio: new Random().nextInt(100),
				apiResponseTimeMs: new Random().nextInt(500),
				activeConnections: new Random().nextInt(1000),
				requestsPerMinute: new Random().nextInt(5000),
				cpuUtilization: new Random().nextInt(100),
				memoryUtilization: new Random().nextInt(100),
				diskIOUtilization: new Random().nextInt(100),
				networkUtilization: new Random().nextInt(100),
				backgroundJobsRunning: new Random().nextInt(50),
				queuedTasks: new Random().nextInt(100),
				completedTasksToday: new Random().nextInt(1000),
				databaseConnections: new Random().nextInt(100),
				databaseResponseTimeMs: new Random().nextInt(50),
				cacheSize: "${new Random().nextInt(1000)} MB",
				lastHealthCheck: new Date().format('yyyy-MM-dd HH:mm:ss'),
				criticalErrors: 0,
				warnings: new Random().nextInt(5),
				infoMessages: new Random().nextInt(50),
				lastBackup: new Date().format('yyyy-MM-dd HH:mm:ss'),
				backupStatus: 'Success',
				replicationLag: "${new Random().nextInt(60)} seconds",
				serviceEndpoints: new Random().nextInt(10) + 5,
				externalIntegrations: new Random().nextInt(15),
				rateLimitStatus: "${90 + new Random().nextInt(10)}% available"
			])

			// Write fake logs that look like they were fetched from an external source
			def systemLogsFile = request.contentsDir['system-api-logs.txt']
			systemLogsFile.text = """[2026-02-14 10:32:15] INFO  - Omega System API connected successfully
[2026-02-14 10:32:16] DEBUG - Fetching system configuration for: ${system.name}
[2026-02-14 10:32:16] INFO  - System status check: OPERATIONAL
[2026-02-14 10:32:17] DEBUG - Retrieved ${system.components?.size() ?: 0} system components
[2026-02-14 10:32:17] INFO  - Health check passed for system ID: ${system.id}
[2026-02-14 10:32:18] DEBUG - System uptime: ${Math.abs(new Random().nextInt(1000))} hours
[2026-02-14 10:32:18] INFO  - Performance metrics collected successfully
[2026-02-14 10:32:19] DEBUG - Cache hit ratio: ${new Random().nextInt(100)}%
[2026-02-14 10:32:19] INFO  - API response time: ${new Random().nextInt(500)}ms
[2026-02-14 10:32:20] INFO  - System diagnostic bundle generation completed
"""

			def diagnosticOutputFile = request.contentsDir['external-diagnostics.log']
			diagnosticOutputFile.text = """Omega System Diagnostic Report
================================
Timestamp: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
System: ${system.name}
Provider: Omega Test System Provider

External Service Status:
- API Endpoint: REACHABLE
- Authentication: VALID
- Rate Limit: 95% available
- Last Sync: ${new Date().format('yyyy-MM-dd HH:mm:ss')}

Component Health:
- Core Services: OK
- Background Jobs: RUNNING
- Database Connection: ACTIVE
- Message Queue: OPERATIONAL

Resource Utilization:
- CPU: ${new Random().nextInt(100)}%
- Memory: ${new Random().nextInt(100)}%
- Disk I/O: ${new Random().nextInt(100)}%
- Network: ${new Random().nextInt(100)}%

Recent Events:
- No critical errors detected
- ${new Random().nextInt(50)} warning(s) in last 24 hours
- ${new Random().nextInt(200)} info messages logged

Diagnostic complete.
"""

			log.info("Successfully generated support bundle contents for Omega system: ${system.name}")
			return ServiceResponse.success()

		} catch (Exception e) {
			log.error("Error generating support bundle contents for Omega system ${system.name}: ${e.message}", e)
			return ServiceResponse.error("Failed to generate support bundle contents: ${e.message}")
		}
	}
}
