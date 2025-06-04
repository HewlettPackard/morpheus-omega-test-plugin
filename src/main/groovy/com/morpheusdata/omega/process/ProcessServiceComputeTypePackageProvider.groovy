package com.morpheusdata.omega.process

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusProcessService
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.ComputeTypePackageProvider
import com.morpheusdata.model.ComputeServerGroup
import com.morpheusdata.model.ComputeServerGroupPackage
import com.morpheusdata.model.Icon
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.ProcessStepUpdate
import com.morpheusdata.request.PackageDeleteRequest
import com.morpheusdata.request.PackageInstallRequest
import com.morpheusdata.request.PackageUpdateRequest
import com.morpheusdata.request.PackageUpgradeRequest
import com.morpheusdata.response.ServiceResponse
import groovy.json.JsonSlurper

import static com.morpheusdata.omega.process.ProcessServiceExamplesDataSource.PROVIDER_KEY
import static com.morpheusdata.omega.process.ProcessServiceExamplesDataSource.PROVIDER_NAMESPACE

class ProcessServiceComputeTypePackageProvider  implements ComputeTypePackageProvider {
	// This code matches what we declared in 'resources/scribe/process-step-type.scribe' and will be seeded in
	// on plugin load
	public static ProcessStepType CUSTOM_STEP_INSTALL = ProcessStepType.forCode("omega.process.custom-package-install")
	public static ProcessStepType CUSTOM_STEP_UPGRADE = ProcessStepType.forCode("omega.process.custom-package-upgrade")
	public static ProcessStepType CUSTOM_STEP_UPDATE = ProcessStepType.forCode("omega.process.custom-package-update")
	public static ProcessStepType CUSTOM_STEP_DELETE = ProcessStepType.forCode("omega.process.custom-package-delete")

	private final Plugin plugin
	private final MorpheusContext context

	ProcessServiceComputeTypePackageProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.context = context
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Icon getCircularIcon() {
		return new Icon(path: 'morpheus.svg', darkPath: 'morpheus.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	List<OptionType> getOptionTypes() {
		[
			/**
			 * Add an option so we can pick the example to run
			 */
			new OptionType(
					name: 'Example To Run',
					code: 'computeTypePackage.omega.process.example',
					fieldContext: 'config',
					fieldName: 'example',
					fieldLabel: 'Example To Run',
					inputType: OptionType.InputType.SELECT,
					displayOrder: 10,
					required: true,
					editable: true,
					noSelection: 'Select',
					optionSourceType: PROVIDER_NAMESPACE,
					optionSource: PROVIDER_KEY
			)
		]
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getDescription() { "" }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getType() { "" }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getPackageType() { "" }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getProviderType() { "mvm" }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getPackageVersion() { "0.1.0" }

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ComputeServerGroupPackage> installPackage(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageInstallRequest packageInstallRequest) {
		def config = new JsonSlurper().parseText(computeServerGroupPackage.config)
		def exampleToRun = ProcessServiceExamplesDataSource.ProcessExample.valueOf(config.example)

		switch (exampleToRun) {
			case ProcessServiceExamplesDataSource.ProcessExample.BASIC:
			case ProcessServiceExamplesDataSource.ProcessExample.BASIC_CUSTOM:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing")
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				break
			case ProcessServiceExamplesDataSource.ProcessExample.UPDATE:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing")
				for (int i = 0; i < 5; i++) {
					def update = new ProcessStepUpdate(
							status: "install sub-step ${i}/5\n",
							output: "install sub-step ${i}/5\n"
					)
					context.services.process.updateProcessStep(packageInstallRequest.process, CUSTOM_STEP_INSTALL, update, true)

					sleep(1000)
				}
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				break
			case ProcessServiceExamplesDataSource.ProcessExample.FAIL:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing")
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_FAILED, "", true)
				return ServiceResponse.error("ended")
			case ProcessServiceExamplesDataSource.ProcessExample.FAIL_UNENDED:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing")
				return ServiceResponse.error("unended")
			case ProcessServiceExamplesDataSource.ProcessExample.FAIL_EXCEPTION:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing")
				throw new Exception("big oof")
			case ProcessServiceExamplesDataSource.ProcessExample.MULTI:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing1")
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing2")
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				break
			case ProcessServiceExamplesDataSource.ProcessExample.FULL:
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing")
				for (int i = 0; i < 5; i++) {
					def update = new ProcessStepUpdate(
							status: "install sub-step ${i}/5\n",
							output: "install sub-step ${i}/5\n"
					)
					context.services.process.updateProcessStep(packageInstallRequest.process, CUSTOM_STEP_INSTALL, update, true)

					sleep(1000)
				}
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing1")
				context.services.process.endProcessStep(packageInstallRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				context.services.process.startProcessStep(packageInstallRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_INSTALL), "installing2")
				break
			case ProcessServiceExamplesDataSource.ProcessExample.NOOP:
				break
		}

		return ServiceResponse.success(computeServerGroupPackage)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ComputeServerGroupPackage> updatePackageConfig(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageUpdateRequest packageUpdateRequest) {
		// pretend this takes a while
		context.services.process.startProcessStep(packageUpdateRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_UPDATE), "updating")
		for (int i = 1; i <= 5; i += 1) {
			def update = new ProcessStepUpdate(
					status: "update sub-step ${i}/5\n",
					output: "update sub-step ${i}/5\n"
			)
			context.services.process.updateProcessStep(packageUpdateRequest.process, CUSTOM_STEP_UPDATE, update, true)

			sleep(1000)
		}
		context.services.process.endProcessStep(packageUpdateRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)

		return ServiceResponse.success(computeServerGroupPackage)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ComputeServerGroupPackage> upgradePackage(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageUpgradeRequest packageUpgradeRequest) {
		// pretend this takes a while
		context.services.process.startProcessStep(packageUpgradeRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_UPGRADE), "upgrading")
		for (int i = 1; i <= 5; i += 1) {
			def update = new ProcessStepUpdate(
					status: "update sub-step ${i}/5\n",
					output: "update sub-step ${i}/5\n"
			)
			context.services.process.updateProcessStep(packageUpgradeRequest.process, CUSTOM_STEP_UPGRADE, update, true)

			sleep(1000)
		}
		context.services.process.endProcessStep(packageUpgradeRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)

		return ServiceResponse.success(computeServerGroupPackage)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse deletePackage(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageDeleteRequest packageDeleteRequest) {

		// pretend this takes a while
		context.services.process.startProcessStep(packageDeleteRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_DELETE), "deleting")
		for (int i = 1; i <= 5; i++) {
			def update = new ProcessStepUpdate(
					status: "delete sub-step ${i}/5\n",
					output: "delete sub-step ${i}/5\n"
			)
			context.services.process.updateProcessStep(packageDeleteRequest.process, CUSTOM_STEP_DELETE, update, true)

			sleep(1000)
		}
		context.services.process.endProcessStep(packageDeleteRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)

		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	MorpheusContext getMorpheus() { context }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Plugin getPlugin() { plugin }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getCode() { "process-service.computeTypePackageProvider" }

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getName() { "Process Service Example - Compute Type Package Provider" }
}
