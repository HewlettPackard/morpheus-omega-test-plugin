package com.morpheusdata.omega.system

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.SystemProvider
import com.morpheusdata.model.Icon
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.system.System
import com.morpheusdata.model.system.SystemComponentType
import com.morpheusdata.model.system.SystemRequest
import com.morpheusdata.model.system.SystemType
import com.morpheusdata.model.system.SystemTypeLayout
import com.morpheusdata.model.process.InsertProcessStepRequest
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.omega.processjob.OmegaProcessJobProvider
import com.morpheusdata.response.ServiceResponse

class OmegaSystemProvider implements SystemProvider {

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

	/**
	 * Pre-initialization validation and preparation. Inserts a process step using
	 * the OmegaProcessJobProvider to test the process job lifecycle during system creation.
	 * Steps are insert-only; SystemService handles dispatch and awaiting completion.
	 */
	@Override
	ServiceResponse prepareInitializeSystem(System system, SystemRequest systemRequest) {
		log.info("OmegaSystemProvider.prepareInitializeSystem called for system: ${system?.name}")
		try {
			if (systemRequest.process) {
				def processService = morpheusContext.services.process
				def stepEvent = new ProcessEvent()
				stepEvent.stepType = ProcessStepType.forCode('omegaSystemPrepare')
				stepEvent.eventTitle = 'Omega Prepare Initialize'
				stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
				stepEvent.jobConfig = [
					outputMessage: "Prepare initialize completed for system ${system?.name}",
					sleepSeconds : 2
				]

				def insertRequest = new InsertProcessStepRequest(systemRequest.process, stepEvent)
				def insertResponse = processService.insertProcessStep(insertRequest)
				log.info("Inserted prepare step with event id: ${insertResponse?.processEventId}")
			}
			return ServiceResponse.success()
		} catch (e) {
			log.error("Error in prepareInitializeSystem: ${e.message}", e)
			return ServiceResponse.error("Omega prepare initialize failed: ${e.message}")
		}
	}

	/**
	 * Post-initialization logic. Inserts a process step to simulate
	 * the initialization work using the ProcessJobProvider.
	 * Steps are insert-only; SystemService handles dispatch and awaiting completion.
	 */
	@Override
	ServiceResponse initializeSystem(System system, SystemRequest systemRequest) {
		log.info("OmegaSystemProvider.initializeSystem called for system: ${system?.name}")
		try {
			if (systemRequest.process) {
				def processService = morpheusContext.services.process
				def stepEvent = new ProcessEvent()
				stepEvent.stepType = ProcessStepType.forCode('omegaSystemInitialize')
				stepEvent.eventTitle = 'Omega Initialize System'
				stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
				stepEvent.jobConfig = [
					outputMessage: "Initialize completed for system ${system?.name}",
					sleepSeconds : 3
				]

				def insertRequest = new InsertProcessStepRequest(systemRequest.process, stepEvent)
				def insertResponse = processService.insertProcessStep(insertRequest)
				log.info("Inserted initialize step with event id: ${insertResponse?.processEventId}")
			}
			return ServiceResponse.success()
		} catch (e) {
			log.error("Error in initializeSystem: ${e.message}", e)
			return ServiceResponse.error("Omega initialize system failed: ${e.message}")
		}
	}

	/**
	 * Pre-update validation and preparation. Inserts a process step to test the
	 * ProcessJobProvider lifecycle during system updates.
	 * Steps are insert-only; SystemService handles dispatch and awaiting completion.
	 */
	@Override
	ServiceResponse prepareUpdateSystem(System system, SystemRequest systemRequest) {
		log.info("OmegaSystemProvider.prepareUpdateSystem called for system: ${system?.name}")
		try {
			if (systemRequest.process) {
				def processService = morpheusContext.services.process
				def stepEvent = new ProcessEvent()
				stepEvent.stepType = ProcessStepType.forCode('omegaSystemPrepareUpdate')
				stepEvent.eventTitle = 'Omega Prepare Update'
				stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
				stepEvent.jobConfig = [
					outputMessage: "Prepare update completed for system ${system?.name}",
					sleepSeconds : 2
				]

				def insertRequest = new InsertProcessStepRequest(systemRequest.process, stepEvent)
				def insertResponse = processService.insertProcessStep(insertRequest)
				log.info("Inserted prepare update step with event id: ${insertResponse?.processEventId}")
			}
			return ServiceResponse.success()
		} catch (e) {
			log.error("Error in prepareUpdateSystem: ${e.message}", e)
			return ServiceResponse.error("Omega prepare update failed: ${e.message}")
		}
	}

	/**
	 * Post-update logic. Inserts a process step to simulate
	 * system update work using the ProcessJobProvider.
	 * Steps are insert-only; SystemService handles dispatch and awaiting completion.
	 */
	@Override
	ServiceResponse updateSystem(System system, SystemRequest systemRequest) {
		log.info("OmegaSystemProvider.updateSystem called for system: ${system?.name}")
		try {
			if (systemRequest.process) {
				def processService = morpheusContext.services.process
				def stepEvent = new ProcessEvent()
				stepEvent.stepType = ProcessStepType.forCode('omegaSystemUpdate')
				stepEvent.eventTitle = 'Omega Update System'
				stepEvent.jobName = OmegaProcessJobProvider.PROVIDER_CODE
				stepEvent.jobConfig = [
					outputMessage: "Update completed for system ${system?.name}",
					sleepSeconds : 3
				]

				def insertRequest = new InsertProcessStepRequest(systemRequest.process, stepEvent)
				def insertResponse = processService.insertProcessStep(insertRequest)
				log.info("Inserted update step with event id: ${insertResponse?.processEventId}")
			}
			return ServiceResponse.success()
		} catch (e) {
			log.error("Error in updateSystem: ${e.message}", e)
			return ServiceResponse.error("Omega update system failed: ${e.message}")
		}
	}

	/**
	 * Cleanup on system deletion. Logs the deletion for testing purposes.
	 */
	@Override
	ServiceResponse deleteSystem(System system) {
		log.info("OmegaSystemProvider.deleteSystem called for system: ${system?.name}")
		return ServiceResponse.success()
	}

	/**
	 * Periodic refresh. Logs the refresh for testing purposes.
	 */
	@Override
	ServiceResponse refreshSystem(System system) {
		log.info("OmegaSystemProvider.refreshSystem called for system: ${system?.name}")
		return ServiceResponse.success()
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
}
