package com.morpheusdata.omega.processjob

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.ProcessJobProvider
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.process.ProcessJobExecutionRequest
import com.morpheusdata.model.process.ProcessJobExecutionResponse
import com.morpheusdata.omega.logging.LogWrapper
import com.morpheusdata.response.ServiceResponse

/**
 * A test ProcessJobProvider that demonstrates the full retry/resume lifecycle.
 * <p>
 * Configurable behavior via option types:
 * <ul>
 *   <li>{@code simulateFailure} — if "true", the step fails (triggering retry or onFail)</li>
 *   <li>{@code sleepSeconds} — how long the step sleeps to simulate work (default 5)</li>
 *   <li>{@code outputMessage} — message forwarded to the next step via nextOpts</li>
 * </ul>
 *
 * @since 1.4.0
 */
class OmegaProcessJobProvider implements ProcessJobProvider {

	public static final String PROVIDER_CODE = 'omega.process-job'

	protected MorpheusContext morpheusContext
	protected Plugin plugin
	protected final LogWrapper log = LogWrapper.instance

	// In-memory overrides for retry-with-inputs testing
	Map<Long, Map> eventConfigOverrides = [:]

	OmegaProcessJobProvider(Plugin plugin, MorpheusContext morpheusContext) {
		super()
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	@Override
	ServiceResponse<ProcessJobExecutionResponse> execute(ProcessJobExecutionRequest request) {
		log.info("Omega ProcessJob executing for event ${request.processEventId}, overrides map keys: ${eventConfigOverrides.keySet()}")
		def opts = request.opts ?: [:]
		log.info("Original opts: ${opts}")

		// Apply any in-memory overrides from retry-with-inputs
		Map overrides = eventConfigOverrides.remove(request.processEventId)
		if (overrides) {
			log.info("Applying config overrides for event ${request.processEventId}: ${overrides}")
			opts = opts + overrides
		} else {
			log.info("No overrides found for event ${request.processEventId}")
		}

		// Simulate work
		Integer sleepSeconds = (opts.sleepSeconds as Integer) ?: 5
		log.info("Sleeping for ${sleepSeconds}s to simulate work...")
		Thread.sleep(sleepSeconds * 1000L)

		// Check if we should simulate failure
		Boolean shouldFail = opts.simulateFailure?.toString()?.equalsIgnoreCase("true")
		if(shouldFail) {
			Integer retryAttempt = (opts.retryAttempt as Integer) ?: 0
			Integer succeedOnAttempt = (opts.succeedOnAttempt as Integer) ?: 0
			if(succeedOnAttempt > 0 && retryAttempt >= succeedOnAttempt) {
				log.info("Retry attempt ${retryAttempt} >= succeedOnAttempt ${succeedOnAttempt}, succeeding now")
			} else {
				log.warn("Simulating failure for event ${request.processEventId} (attempt ${retryAttempt})")
				return ServiceResponse.error("Simulated failure on attempt ${retryAttempt}")
			}
		}

		// Build response with nextOpts for downstream steps
		String outputMessage = opts.outputMessage ?: "Omega step completed successfully"
		def response = new ProcessJobExecutionResponse()
		response.nextOpts = [
			previousStepMessage: outputMessage,
			completedAt        : new Date().toString(),
			processEventId     : request.processEventId
		]

		log.info("Omega ProcessJob completed successfully for event ${request.processEventId}")
		return ServiceResponse.success(response)
	}

	@Override
	ServiceResponse onFail(ProcessJobExecutionRequest request) {
		log.warn("Omega ProcessJob onFail called for event ${request.processEventId} — cleaning up side effects")
		// In a real provider, you'd clean up any resources created during execute()
		return ServiceResponse.success()
	}

	@Override
	Boolean isRetryable() {
		return true
	}

	@Override
	Integer getRetryCount() {
		return 3
	}

	@Override
	Integer getRetryDelaySeconds() {
		return 10
	}

	@Override
	Boolean isCancelable() {
		return true
	}

	@Override
	List<OptionType> getOptionTypes() {
		return [
			new OptionType(
				code: 'omega.processJob.simulateFailure',
				name: 'Simulate Failure',
				fieldName: 'simulateFailure',
				fieldContext: 'config',
				fieldLabel: 'Simulate Failure',
				inputType: OptionType.InputType.CHECKBOX,
				displayOrder: 0,
				helpBlock: 'When checked, the step will fail (triggering retry).'
			),
			new OptionType(
				code: 'omega.processJob.succeedOnAttempt',
				name: 'Succeed On Attempt',
				fieldName: 'succeedOnAttempt',
				fieldContext: 'config',
				fieldLabel: 'Succeed On Attempt',
				inputType: OptionType.InputType.NUMBER,
				displayOrder: 1,
				helpBlock: 'If simulating failure, succeed after this many retry attempts (0 = never auto-succeed).'
			),
			new OptionType(
				code: 'omega.processJob.sleepSeconds',
				name: 'Sleep Seconds',
				fieldName: 'sleepSeconds',
				fieldContext: 'config',
				fieldLabel: 'Sleep Seconds',
				inputType: OptionType.InputType.NUMBER,
				defaultValue: '5',
				displayOrder: 2,
				helpBlock: 'How many seconds to sleep simulating work.'
			),
			new OptionType(
				code: 'omega.processJob.outputMessage',
				name: 'Output Message',
				fieldName: 'outputMessage',
				fieldContext: 'config',
				fieldLabel: 'Output Message',
				inputType: OptionType.InputType.TEXT,
				defaultValue: 'Omega step completed successfully',
				displayOrder: 3,
				helpBlock: 'Message forwarded to the next step via nextOpts.'
			)
		]
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
		return PROVIDER_CODE
	}

	@Override
	String getName() {
		return "Omega Process Job Test"
	}

	@Override
	String getDescription() {
		return "A test process job provider that demonstrates retry/resume lifecycle with configurable failure simulation."
	}
}
