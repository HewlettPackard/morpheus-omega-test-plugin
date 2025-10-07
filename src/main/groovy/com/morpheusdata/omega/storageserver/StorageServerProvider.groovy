package com.morpheusdata.omega.storageserver


import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.StorageProvider
import com.morpheusdata.core.providers.StorageProviderVolumes
import com.morpheusdata.model.Icon
import com.morpheusdata.model.OptionType;
import com.morpheusdata.model.StorageGroup
import com.morpheusdata.model.StorageServer
import com.morpheusdata.model.StorageServerType
import com.morpheusdata.model.StorageVolume
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.Renderer
import groovy.json.JsonOutput

class StorageServerProvider implements StorageProvider, StorageProviderVolumes{

	public static final String STORAGE_PROVIDER_CODE = 'omega.sstp'

	protected MorpheusContext morpheusContext
	protected Plugin plugin
	protected final LogWrapper log = LogWrapper.instance

	StorageServerProvider(Plugin plugin, MorpheusContext morpheusContext) {
		super()
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	/**
	 * Returns the description of the provider type
	 * @return String
	 */
	@Override
	String getDescription() {
		return 'This is a custom storage provider for Morpheus Omega Test Plugin.'
	}

	/**
	 * Returns the Storage Server Integration logo for display when a user needs to view or add this integration
	 * @return Icon representation of assets stored in the src/assets of the project.
	 */
	@Override
	Icon getIcon() {
		return new Icon(path:"morpheus.svg", darkPath: "morpheus.svg")
	}

	/**
	 * Provide a {@link StorageServerType} to be added to the morpheus environment
	 * as the type for this {@link StorageServer}. The StorageServerType also defines the
	 * OptionTypes for configuration of a new server and its volume types.
	 * @return StorageServerType
	 */
	@Override
	StorageServerType getStorageServerType() {
		StorageServerType storageServerType = new StorageServerType(
                        code: getCode(), name: getName(), description: getDescription(), hasBlock: true, hasObject: false,
                        hasFile: false, hasDatastore: true, hasNamespaces: false, hasGroups: true, hasDisks: true, hasHosts: false,
                        createBlock: true, createObject: false, createFile: false, createDatastore: true, createNamespaces: false,
                        createGroup: true, createDisk: true, createHost: false, hasFileBrowser: true)
                storageServerType.optionTypes = getStorageServerOptionTypes()
                storageServerType.volumeTypes = getStorageVolumeTypes()
                return storageServerType
	}

	Collection<OptionType> getStorageServerOptionTypes() {
		def optionTypes = []
		optionTypes.push(new OptionType(code:'omega.storageServer.global.serviceUrl', name:'serviceUrl', category:'storageServer.global',
				fieldName:'serviceUrl', fieldCode: 'gomorpheus.optiontype.Url', fieldLabel:'URL', fieldContext:'domain', required:true, enabled:true, editable:true, global:false,
				placeHolder:null, helpBlock:'', defaultValue:null, custom:false, displayOrder:0, fieldClass:"storage-server-url", wrapperClass:null))
		optionTypes.push(new OptionType(code:'omega.storageServer.global.credential', name:'credentials', optionSource:'credentials',
				category:'accountIntegrationType.global', fieldName:'type', fieldCode:'gomorpheus.label.credentials', fieldLabel:'Credentials',
				fieldContext:'credential', required:true, enabled:true, editable:true, global:false, placeHolder:null, helpBlock:'', defaultValue:'local',
				custom:false, displayOrder:5, fieldClass:null, wrapperClass:null, config:JsonOutput.toJson(credentialTypes:['username-password']).toString()))
		optionTypes.push(new OptionType(code:'omega.storageServer.global.serviceUsername', name:'serviceUsername', category:'storageServer.global',
				fieldName:'serviceUsername', fieldCode: 'gomorpheus.optiontype.Username', fieldLabel:'Username', fieldContext:'domain', required:true, enabled:true, editable:true, global:false,
				placeHolder:null, helpBlock:'', defaultValue:null, custom:false, displayOrder:10, fieldClass:"storage-server-username", wrapperClass:null, localCredential:true))
		optionTypes.push(new OptionType(code:'omega.storageServer.global.servicePassword', name:'servicePassword', category:'storageServer.global',
				fieldName:'servicePassword', fieldCode: 'gomorpheus.optiontype.Password', fieldLabel:'Password', fieldContext:'domain', required:true, enabled:true, editable:true, global:false,
				placeHolder:null, helpBlock:'', defaultValue:null, custom:false, displayOrder:15, fieldClass:"storage-server-password", wrapperClass:null, localCredential:true))
		return optionTypes
	}

	/**
	 * Validation Method used to validate all inputs applied to the integration of a Storage Provider upon save.
	 * If an input fails validation or authentication information cannot be verified, Error messages should be returned
	 * via a {@link ServiceResponse} object where the key on the error is the field name and the value is the error message.
	 * If the error is a generic authentication error or unknown error, a standard message can also be sent back in the response.
	 *
	 * @param storageServer The Storage Server object contains all the saved information regarding configuration of the Storage Provider
	 * @param opts an optional map of parameters that could be sent. This may not currently be used and can be assumed blank
	 * @return A response is returned depending on if the inputs are valid or not.
	 */
	@Override
	ServiceResponse verifyStorageServer(StorageServer storageServer, Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * Called on the first save / update of a storage server integration. Used to do any initialization of a new integration
	 * Often times this calls the periodic refresh method directly.
	 * @param storageServer The Storage Server object contains all the saved information regarding configuration of the Storage Provider.
	 * @param opts an optional map of parameters that could be sent. This may not currently be used and can be assumed blank
	 * @return a ServiceResponse containing the success state of the initialization phase
	 */
	@Override
	ServiceResponse initializeStorageServer(StorageServer storageServer, Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * Refresh the provider with the associated data in the external system.
	 * @param storageServer The Storage Server object contains all the saved information regarding configuration of the Storage Provider.
	 * @param opts an optional map of parameters that could be sent. This may not currently be used and can be assumed blank
	 * @return a {@link ServiceResponse} object. A ServiceResponse with a success value of 'false' will indicate the
	 * refresh process has failed and will change the storage server status to 'error'
	 */
	@Override
	ServiceResponse refreshStorageServer(StorageServer storageServer, Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * Returns the Morpheus Context for interacting with data stored in the Main Morpheus Application
	 *
	 * @return an implementation of the MorpheusContext for running Future based rxJava queries
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.@morpheusContext
	}

	/**
	 * Returns the instance of the Plugin class that this provider is loaded from
	 * @return Plugin class contains references to other providers
	 */
	@Override
	Plugin getPlugin() {
		return this.@plugin
	}

	/**
	 * A unique shortcode used for referencing the provided provider. Make sure this is going to be unique as any data
	 * that is seeded or generated related to this provider will reference it by this code.
	 * @return short code string that should be unique across all other plugin implementations.
	 */
	@Override
	String getCode() {
		return STORAGE_PROVIDER_CODE
	}

	/**
	 * Provides the provider name for reference when adding to the Morpheus Orchestrator
	 * NOTE: This may be useful to set as an i18n key for UI reference and localization support.
	 *
	 * @return either an English name of a Provider or an i18n based key that can be scanned for in a properties file.
	 */
	@Override
	String getName() {
		return "Storage Server Test Provider"
	}

	@Override
	ServiceResponse<StorageVolume> createVolume(StorageGroup storageGroup, StorageVolume storageVolume, Map opts) {
		// unused see createVolume(StorageServer storageServer, StorageVolume storageVolume, Map opts)
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		storageVolume = morpheusContext.services.storage.volume.create(storageVolume)
		return ServiceResponse.success(storageVolume)
	}

	@Override
	ServiceResponse<StorageVolume> createVolume(StorageServer storageServer, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		storageVolume = morpheusContext.services.storage.volume.create(storageVolume)
		return ServiceResponse.success(storageVolume);
	}

	@Override
	ServiceResponse<StorageVolume> resizeVolume(StorageGroup storageGroup, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		storageVolume.maxStorage = Long.parseLong(opts.maxStorage)
		morpheusContext.services.storage.volume.save(storageVolume)
		return ServiceResponse.create([success: true, data: [storageVolume: storageVolume]])
	}

	@Override
	ServiceResponse<StorageVolume> resizeVolume(StorageServer storageServer, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		storageVolume.maxStorage = Long.parseLong(opts.maxStorage)
		morpheusContext.services.storage.volume.save(storageVolume)
		return ServiceResponse.create([success: true, data: [storageVolume: storageVolume]])
	}

	@Override
	ServiceResponse<StorageVolume> updateVolume(StorageGroup storageGroup, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		storageVolume = morpheusContext.services.storage.volume.save(storageVolume)
		return ServiceResponse.create([success: true, data: [storageVolume: storageVolume]])
	}

	@Override
	ServiceResponse<StorageVolume> updateVolume(StorageServer storageServer, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		storageVolume = morpheusContext.services.storage.volume.save(storageVolume)
		return ServiceResponse.create([success: true, data: [storageVolume: storageVolume]])
	}

	@Override
	ServiceResponse<StorageVolume> deleteVolume(StorageGroup storageGroup, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		// Core takes care of the deletion from the DB for us and this would be the place to deal with deletion on the remote storage
		// morpheusContext.services.storage.volume.remove(storageVolume)
		return ServiceResponse.success(storageVolume)
	}

	@Override
	ServiceResponse<StorageVolume> deleteVolume(StorageServer storageServer, StorageVolume storageVolume, Map opts) {
		log.info("Dump of param storageVolume: ${storageVolume?.dump()}")
		log.info("Options: ${opts?.dump()}")
		// Core takes care of the deletion from the DB for us and this would be the place to deal with deletion on the remote storage
		// morpheusContext.services.storage.volume.remove(storageVolume)
		return ServiceResponse.success(storageVolume)
	}

	@Override
	Collection<StorageVolumeType> getStorageVolumeTypes() {
		def blockStorageType = new StorageVolumeType(
			name: 'Block Storage',
			code: 'omega.sstp.block',
			description: 'Block storage type for the SSTP plugin',
			displayName: 'SSTP Block Storage',
			volumeType: "volume",
			displayOrder: 1,
			customLabel: false,
			customSize: true,
			defaultType: true,
			autoDelete: true,
			hasDatastore: true,
			allowSearch: true,
			volumeCategory: "block",
			deletable: true,
			editable: true,
			nameEditable: true,
			resizable: true,
			planResizable: false,
			minStorage: 1L * 1024L * 1024L * 1024L, // 1 GB
			maxStorage: 100L * 1024L * 1024L * 1024L, // 100 GB
			configurableIOPS: true,
			minIOPS: 10,
			maxIOPS: 100000,
			multiAttachSupported: true,
			optionTypes: [
				new OptionType(
					code: 'omega.sstp.block.provisionType',
					name: 'Provision Type',
					category: 'sstp.block',
					fieldName: 'provisionType',
					fieldCode: 'gomorpheus.optiontype.ProvisionType',
					fieldLabel: 'Provision Type',
					fieldContext: 'domain',
					required: true,
					enabled: true,
					editable: true,
					global: false,
					placeHolder: null,
					helpBlock: '',
					defaultValue: null,
					custom: false,
					displayOrder: 1,
					fieldClass: null,
					wrapperClass: null
				),
				new OptionType(
						code: 'omega.sstp.block.volumeSize',
						name: 'Volume Size',
						category: 'sstp.block',
						fieldName: 'maxStorage',
						fieldCode: 'gomorpheus.optiontype.VolumeSize',
						fieldLabel: 'Volume Size',
						fieldContext: 'domain',
						required: true,
						enabled: true,
						editable: true,
						global: false,
						placeHolder: null,
						helpBlock: '',
						defaultValue: null,
						custom: false,
						displayOrder: 2,
						fieldClass: null,
						fieldAddOn: 'MB',
						wrapperClass: null,
						inputType: OptionType.InputType.NUMBER,
						minVal: 1
				),
				new OptionType(
						name: 'sharedVolume',
						code: 'omega.sstp.block.shared-volume',
						fieldLabel: 'Shared Volume',
						fieldName: 'sharedVolume',
						inputType: OptionType.InputType.CHECKBOX,
						displayOrder: 4,
						editable: true,
						config: JsonOutput.toJson(
								[
									resizable: true,
								]
						).toString(),
				),
				new OptionType(
						name: "computeServer",
						code: 'omega.sstp.block.computeserver',
						fieldLabel: 'Compute Server',
						fieldName:'computeServer',
						inputType: OptionType.InputType.SELECT,
						displayOrder: 5,
						optionSourceType: 'example',
						optionSource: 'collectionDatasetExample',
						dependsOnCode: 'config.sharedVolume',
						visibleOnCode: 'config.sharedVolume:off',
						editable: true,
						config: JsonOutput.toJson(
								[
									resizable: true,
								]
						).toString(),
				),
				new OptionType(
						name: "BMaaS Instances",
						code: 'omega.sstp.block.instances',
						fieldLabel: 'Instances',
						fieldName: 'instances',
						inputType: OptionType.InputType.MULTI_SELECT,
						displayOrder: 6,
						optionSourceType: 'example',
						optionSource: 'collectionDatasetExample',
						dependsOnCode: 'config.sharedVolume',
						visibleOnCode: 'config.sharedVolume:on',
						editable: true,
						config: JsonOutput.toJson(
								[
									resizable: true,
								]
						).toString(),
				)
			]
		)
		return [blockStorageType]
	}

	Renderer<?> getRenderer() {
		return null;
	}
}
