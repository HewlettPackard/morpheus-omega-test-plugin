package com.morpheusdata.omega.addon

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusProcessService
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.ComputeTypePackageProvider
import com.morpheusdata.model.ComputeServerGroup
import com.morpheusdata.model.ComputeServerGroupPackage
import com.morpheusdata.model.ComputeTypePackage
import com.morpheusdata.model.Icon
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.ProcessStepUpdate
import com.morpheusdata.request.PackageDeleteRequest
import com.morpheusdata.request.PackageInstallRequest
import com.morpheusdata.request.PackageUpgradeRequest
import com.morpheusdata.response.ServiceResponse

class AddonPackageTypeProvider implements ComputeTypePackageProvider {

		public static final String PACKAGE_PROVIDER_CODE = 'omega.addon.package-test-type'

		protected MorpheusContext morpheusContext
		protected Plugin plugin

		AddonPackageTypeProvider(Plugin plugin, MorpheusContext morpheusContext) {
				this.morpheusContext = morpheusContext
				this.plugin = plugin
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		boolean isPlugin() {
				return true
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		MorpheusContext getMorpheus() {
				return morpheusContext
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		Plugin getPlugin() {
				return plugin
		}

		/**
		 * {@inheritDoc}
		 */
		String getName() {
				"Omega Test"
		}

		/**
		 * {@inheritDoc}
		 */
		String getCode() {
				return PACKAGE_PROVIDER_CODE
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		String getDescription() {
				return "A quick test package that does nothing, just provides option types"
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		String getType() {
				return "test"
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		String getPackageType() {
				return "test"
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		String getProviderType() {
				return ComputeTypePackage.ProviderType.MVM
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		String getPackageVersion() {
				return "0.0.2"
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		Icon getCircularIcon() {
				return new Icon(path: 'omega-circular.svg', darkPath: 'omega-circular-dark.svg')
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		List<OptionType> getOptionTypes() {
				return [
						new OptionType(
								name: 'Path',
								code: 'morpheus-addon-package-test-type-path',
								fieldName: 'path',
								displayOrder: 0,
								fieldLabel: 'Path',
								required: true,
								inputType: OptionType.InputType.TEXT,
								fieldContext: 'config'
						)
				]
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		ServiceResponse validatePackageConfig(ComputeServerGroup serverGroup, ComputeServerGroupPackage serverGroupPackage) {
				def rtn = [success: true, errors: [:]]
				if( serverGroupPackage.getConfigProperty('path')?.contains('http') ) {
						rtn.msg = "Invalid path"
						rtn.success = false
				}
				return ServiceResponse.create(rtn)
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		ServiceResponse<ComputeServerGroupPackage> installPackage(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageInstallRequest packageInstallRequest) {
				println "\u001B[33mAC Log - TestAddonPackageTypeProvider:installPackage- called on ${serverGroup.name} to install ${computeServerGroupPackage.packageType.name}:${computeServerGroupPackage.packageType.packageVersion} config: ${computeServerGroupPackage.config}\u001B[0m"
				return ServiceResponse<ComputeServerGroupPackage>.success(computeServerGroupPackage)
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		ServiceResponse<ComputeServerGroupPackage> upgradePackage(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageUpgradeRequest packageUpgradeRequest) {
				println "\u001B[33mAC Log - TestAddonPackageTypeProvider:upgradePackage- called on ${serverGroup.name} to delete ${computeServerGroupPackage.packageType.name}:${computeServerGroupPackage.packageType.packageVersion}\u001B[0m"
				morpheusContext.services.process.startProcessStep(packageUpgradeRequest.process, new ProcessEvent(stepType: ProcessStepType.EXECUTE_ACTION), "upgrading")
				for (int i = 1; i <= 2; i += 1) {
						def update = new ProcessStepUpdate(
										status: "update sub-step ${i}/2\n",
										output: "update sub-step ${i}/2\n"
						)
						morpheusContext.services.process.updateProcessStep(packageUpgradeRequest.process, ProcessStepType.EXECUTE_ACTION, update, true)

						sleep(1000)
				}
				morpheusContext.services.process.endProcessStep(packageUpgradeRequest.process, MorpheusProcessService.STATUS_COMPLETE, "", true)
				computeServerGroupPackage.packageVersion = packageUpgradeRequest.newVersion
				return ServiceResponse<ComputeServerGroupPackage>.success(computeServerGroupPackage)
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		ServiceResponse deletePackage(ComputeServerGroup serverGroup, ComputeServerGroupPackage computeServerGroupPackage, PackageDeleteRequest packageDeleteRequest) {
				println "\u001B[33mAC Log - TestAddonPackageTypeProvider:deletePackage- called on ${serverGroup.name} to delete ${computeServerGroupPackage.packageType.name}:${computeServerGroupPackage.packageType.packageVersion}\u001B[0m"
				return ServiceResponse.success()
		}

}
