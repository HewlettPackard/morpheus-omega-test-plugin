package com.morpheusdata.omega.process

import com.morpheusdata.PrepareHostResponse

import com.morpheusdata.core.AbstractProvisionProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.MorpheusProcessService
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.HostProvisionProvider
import com.morpheusdata.core.providers.WorkloadProvisionProvider
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.Icon
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ProcessEvent
import com.morpheusdata.model.ProcessStepType
import com.morpheusdata.model.ProcessStepUpdate
import com.morpheusdata.model.ServicePlan
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.model.Workload
import com.morpheusdata.model.provisioning.HostRequest
import com.morpheusdata.model.provisioning.WorkloadRequest
import com.morpheusdata.response.PrepareWorkloadResponse
import com.morpheusdata.response.ProvisionResponse
import com.morpheusdata.response.ServiceResponse
import groovy.util.logging.Slf4j

import static com.morpheusdata.omega.process.ProcessServiceExamplesDataSource.*


@Slf4j
class ProcessServiceExampleProvisionProvider extends AbstractProvisionProvider implements WorkloadProvisionProvider, HostProvisionProvider, HostProvisionProvider.finalizeHostFacet {
	public static final String PROVISION_PROVIDER_CODE = 'omega.process.provision'
	// This code matches what we declared in 'resources/scribe/process-step-type.scribe' and will be seeded in
	// on plugin load
	public static ProcessStepType CUSTOM_STEP_TYPE = ProcessStepType.forCode("omega.process.custom-step")
	public static ProcessStepType CUSTOM_STEP_FIRST_TYPE = ProcessStepType.forCode("omega.process.custom-step-first")
	public static ProcessStepType CUSTOM_STEP_SECOND_TYPE = ProcessStepType.forCode("omega.process.custom-step-second")

	protected MorpheusContext context
	protected Plugin plugin


	ProcessServiceExampleProvisionProvider(Plugin plugin, MorpheusContext ctx) {
		super()
		this.@context = ctx
		this.@plugin = plugin
	}

	/**
	 * {@inheritDoc}
	 *
	 * This is an example plugin without "real" provisioning, we don't support an agent install. This lets us
	 * skip the network wait.
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
				new PrepareWorkloadResponse(workload: workload) // adding the workload to the response for convenience
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
		return new Icon(path: 'omega-circular.svg', darkPath: 'omega-circular-dark.svg')
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<OptionType> getOptionTypes() {
		[
				/**
				 * Add an option so we can pick the example to run
				 */
				new OptionType(
						name: 'Example To Run',
						code: 'provisionType.omega.process.example',
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
	Collection<StorageVolumeType> getDataVolumeStorageTypes() { [] }

	/**
	 * {@inheritDoc}
	 */
	@Override
	Collection<ServicePlan> getServicePlans() {
		[
				new ServicePlan(
						code: 'omega.process.custom',
						editable: true,
						name: 'Stub Any',
						description: 'Any Server',
						sortOrder: 0,
						maxCores: 1,
						maxStorage: 0l,
						maxMemory: 0l,
						maxCpu: 1,
						customMaxStorage: false,
						customMaxDataStorage: false,
						addVolumes: false
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
		// Load the example we're supposed to run. This was specified in the provisioning wizard
		def exampleToRun = ProcessExample.valueOf(workload.configMap.example)
		switch (exampleToRun) {
			//  This is a example of adding a step using an existing ProcessStepType provided by morpheus
			case ProcessExample.BASIC:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: ProcessStepType.EXECUTE_ACTION), "").blockingGet()
				context.async.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "basic output", true).blockingGet()
				break
			// This is a example of adding a step using a custom ProcessStepType provided by this plugin
			case ProcessExample.BASIC_CUSTOM:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_TYPE), "").blockingGet()
				context.async.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "basic custom output", true).blockingGet()
				break
		  // This is a example of updating a step over a period of time using a custom ProcessStepType provided by this plugin
			case ProcessExample.UPDATE:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_TYPE), "").blockingGet()
				def totalTime = 60000
				def increment = 10000
				for (int i = 0; i < totalTime; i += increment) {
					def update = new ProcessStepUpdate(
							status: "running (${i}ms)",
							output: "output@${i}ms\n",
							message: "message@${i}ms\n",
					)
					context.async.process.updateProcessStep(workloadRequest.process, CUSTOM_STEP_TYPE, update, true).blockingGet()

					sleep(increment)
				}
				context.async.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "final", true).blockingGet()
				break
			// This is a example of failing a custom ProcessStepType provided by this plugin
			case ProcessExample.FAIL:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_TYPE), "").blockingGet()
				def update = new ProcessStepUpdate(
						error: "failure by design\n",
				)
				context.async.process.updateProcessStep(workloadRequest.process, CUSTOM_STEP_TYPE, update, true).blockingGet()
				context.async.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_FAILED, "final", true).blockingGet()
				return new ServiceResponse<ProvisionResponse>(
						true,
						null, // no message
						null, // no errors
						new ProvisionResponse(success: true, installAgent: false, skipNetworkWait: true, noAgent: true)
				)
				// This is a example of failing a custom ProcessStepType provided by this plugin
			case ProcessExample.FAIL_UNENDED:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_TYPE), "").blockingGet()
				return ServiceResponse.error("failure by design")
			case ProcessExample.FAIL_EXCEPTION:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_TYPE), "").blockingGet()
				throw new Exception("failure by exception")
			// This is a example of starting/ending multiple steps
			case ProcessExample.MULTI:
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_FIRST_TYPE), "").blockingGet()
				context.async.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "final first", true).blockingGet()
				context.async.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_SECOND_TYPE), "").blockingGet()
				context.async.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "final second", true).blockingGet()
				break
			// An example combining all the other examples as one. This uses the synchronous API rather than the async one used above (i.e., context.services)
			case ProcessExample.FULL:
				// existing process step type
				context.services.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: ProcessStepType.EXECUTE_ACTION), "")
				context.services.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "basic output", true)

				// custom process step type
				context.services.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_FIRST_TYPE), "")
				context.services.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "custom", true)

				// update a process step
				context.services.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_TYPE), "")
				def totalTime = 60000
				def increment = 10000
				for (int i = 0; i < totalTime; i += increment) {
					def update = new ProcessStepUpdate(
							status: "running (${i}ms)",
							output: "output@${i}ms\n",
					)
					context.services.process.updateProcessStep(workloadRequest.process, CUSTOM_STEP_TYPE, update, true)

					sleep(increment)
				}
				context.services.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "final", true)

				// If a step wasn't completed, it'll be automatically ended for us when the next is started, but we have less control over the final
				// output and and status
				context.services.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_FIRST_TYPE), "")
				context.services.process.startProcessStep(workloadRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_SECOND_TYPE), "")
				context.services.process.endProcessStep(workloadRequest.process, MorpheusProcessService.STATUS_COMPLETE, "final", false)
				break
			case ProcessExample.NOOP:
				// do nothing intentionally
				break
		}

		return new ServiceResponse<ProvisionResponse>(
				true,
				null, // no message
				null, // no errors
				new ProvisionResponse(success: true, installAgent: false, skipNetworkWait: true, noAgent: true)
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
		// Generally a call to stopWorkLoad() and then startWorkload()
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse removeWorkload(Workload workload, Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> getServerDetails(ComputeServer server) {
		return new ServiceResponse<ProvisionResponse>(true, null, null, new ProvisionResponse(success: true))
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
		return 'Omega Process Service'
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getDefaultInstanceTypeDescription() { "Provision example process service usecases." }

	/**
	 * {@inheritDoc}
	 *
	 * We're not really provisioning, so don't make the user pick an image
	 */
	@Override
	Boolean requiresVirtualImage() { false }

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse validateHost(ComputeServer server, Map opts) { ServiceResponse.success() }

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<PrepareHostResponse> prepareHost(ComputeServer server, HostRequest hostRequest, Map opts) {
		context.services.process.startProcessStep(hostRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_FIRST_TYPE), "")
		return ServiceResponse.success(new PrepareHostResponse(computeServer: server, options: opts))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> runHost(ComputeServer server, HostRequest hostRequest, Map opts) {
		context.services.process.startProcessStep(hostRequest.process, new ProcessEvent(stepType: CUSTOM_STEP_SECOND_TYPE), "")
		return ServiceResponse.success(new ProvisionResponse(installAgent: false, noAgent: true))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse<ProvisionResponse> waitForHost(ComputeServer server) {
		return ServiceResponse.success(new ProvisionResponse(installAgent: false, noAgent: true, skipNetworkWait: true))
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ServiceResponse finalizeHost(ComputeServer server) {
		server.status = 'available'
		context.services.computeServer.save(server)
		return ServiceResponse.success()
	}
}
