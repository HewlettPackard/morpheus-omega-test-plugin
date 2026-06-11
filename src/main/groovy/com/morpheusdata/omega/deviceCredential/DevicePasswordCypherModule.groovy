package com.morpheusdata.omega.deviceCredential

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.cypher.Cypher
import com.morpheusdata.cypher.CypherModule
import com.morpheusdata.cypher.CypherObject
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * CypherModule implementation for device password management. Registered at the
 * "ilo-device" mount point via {@link DevicePasswordCypherProvider}.
 *
 * <h3>Key format (relative to mount):</h3>
 * <ul>
 *   <li>Base key:    {@code credential/v1/{credentialId}}</li>
 *   <li>Link key:    {@code credential/v1/{credentialId}/{refType}/{refUuid}}</li>
 *   <li>Pending key: {@code credential-pending/v1/{credentialId}/{refType}/{refUuid}}</li>
 * </ul>
 *
 * <h3>Value format (JSON):</h3>
 * Username and password are stored together in the value for atomic updates.
 * <pre>{@code
 * {
 *   "username": "admin",
 *   "password": "newSecurePassword",
 *   "refType": "ComputeServer",
 *   "refId": "some-uuid-here"
 * }
 * }</pre>
 *
 *
 * <h3>Atomicity:</h3>
 * If device push fails, {@code write()} returns {@code null} and nothing is persisted.
 */
@Slf4j
class DevicePasswordCypherModule implements CypherModule {
	Cypher cypher
	MorpheusContext morpheusContext
	Plugin plugin

	void setMorpheusContext(MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext
	}

	void setPlugin(Plugin plugin) {
		this.plugin = plugin
	}

	@Override
	void setCypher(Cypher cypher) {
		this.cypher = cypher
	}

	@Override
	Boolean readFromDatastore() {
		return true
	}

	@Override
	CypherObject write(String relativeKey, String path, String value, Long leaseTimeout, String leaseObjectRef, String createdBy) {
		log.info("Received write to ilo-device cypher with key: {}, path: {}, value: {}", relativeKey, path, value)
		if (!value) {
			log.warn("Empty value provided for ilo-device write at key: {}", relativeKey)
			return null
		}

		// should add a version check for future schema changes, but for now we assume all writes conform to the expected format
		String key = path ? "${path}/${relativeKey}" : relativeKey

		// Parse the key: credential/v1/{credentialId} or credential/v1/{credentialId}/{refType}/{refUuid}
		def keyContext = parseRelativeKey(relativeKey)

		Map payload
		try {
			payload = new JsonSlurper().parseText(value) as Map
		} catch (Exception ex) {
			log.error("Failed to parse ilo-device value as JSON for key: {}", relativeKey, ex)
			return null
		}

		log.info("Parsed payload for ilo-device write at key ${relativeKey}: ${payload}")
		String username = payload.username as String
		String password = payload.password as String

		String refType = payload.refType as String ?: keyContext.refType
		String refId = payload.refId as String ?: keyContext.refId

		if ((!username || !password) && !payload.pendingPayloadHash) {
			log.error("Missing required username or password in ilo-device payload for key: {}", relativeKey)
			return null
		}

		if (payload.pendingPayloadHash) {
			log.info("Pending credential update for ilo-device key: {}. Payload hash: {}", relativeKey, payload.pendingPayloadHash)
		} else if (refId) {
			// simulate random failure
//			if (new Random().nextBoolean()) {
//				log.error("Simulated device push failure for key: {}. Nothing will be persisted (atomic failure).", relativeKey)
//				return null
//			}

			def currentValue = cypher.read(key)
			// if there is no current value it is a new credential link, so we should check for a base credential value to push to the device
			if (!currentValue) {
				String baseKey = "credential/v1/${keyContext.credentialId}"
				currentValue = cypher.read(baseKey)
				log.info("No existing value found for ilo-device key: {}. Checking base credential key: {}", relativeKey, baseKey)
			}
			if (currentValue) {
				try {
					def currentPayload = new JsonSlurper().parseText(currentValue.value) as Map
					String currentUsername = currentPayload.username as String
					String currentPassword = currentPayload.password as String

					boolean pushSuccess = pushPasswordToDevice(refType, refId, currentUsername, currentPassword, username, password)
					if (!pushSuccess) {
						log.error("Device password push failed for key: {}. Nothing will be persisted (atomic failure).", relativeKey)
						return null
					}
				} catch (Exception ex) {
					log.warn("Unable to parse existing value for ilo-device key {}: {}", relativeKey, ex.message)
					return null
				}
			} else {
				log.info("No existing credentials found for ilo-device key: {}", relativeKey)
			}
		} else {
			log.info("Base key write for credential: {}", keyContext.credentialId)
		}

		String persistValue = new JsonBuilder(payload).toString()
		CypherObject cypherObject = new CypherObject(key, persistValue, leaseTimeout, leaseObjectRef, createdBy)
		return cypherObject
	}

	@Override
	CypherObject read(String relativeKey, String path, Long leaseTimeout, String leaseObjectRef, String createdBy) {
		log.info("Read requested for ilo-device cypher with key: {}, path: {}", relativeKey, path)
		return null
	}

	@Override
	boolean delete(String relativeKey, String path, CypherObject object) {
		log.info("Deleting ilo-device cypher key: {}", relativeKey)
		return true
	}

	@Override
	String getUsage() {
		return 'Stores device iLO credentials and pushes to device on write. ' +
			'Key format: ilo-device/credential/v1/{credentialId}/{refType}/{refUuid}. ' +
			'Value is JSON with username and password.'
	}

	@Override
	String getHTMLUsage() {
		return '''<p>Stores device iLO credentials and pushes to device on write.</p>
			<p>Key format: <code>ilo-device/credential/v1/{credentialId}/{refType}/{refUuid}</code></p>
			<p>Value format: <code>{"username":"admin","password":"secret"}</code></p>
			<ul>
				<li>When <b>refType/refUuid</b> are present: pushes to device, then persists</li>
				<li>When absent (base key): base credential storage (no device push)</li>
			</ul>'''
	}

	/**
	 * Parse relative key into its components.
	 * Expected formats:
	 *   credential/v1/{credentialId}                   — base key
	 *   credential/v1/{credentialId}/{refType}/{refUuid} — link key
	 *   credential-pending/v1/{credentialId}/...        — pending marker (ignored by write logic)
	 */
	private Map parseRelativeKey(String relativeKey) {
		def parts = relativeKey?.split('/') ?: []
		def result = [keyType: null, version: null, credentialId: null, refType: null, refId: null]
		if (parts.length >= 3) {
			result.keyType = parts[0]       // "credential" or "credential-pending"
			result.version = parts[1]       // "v1"
			result.credentialId = parts[2]  // credential ID
		}
		if (parts.length >= 5) {
			result.refType = parts[3]       // refType
			result.refId = parts[4]         // refUuid
		}
		return result
	}

	private boolean pushPasswordToDevice(String refType, String refId, String username, String password, String newUsername, String newPassword) {
		// Simulate device push logic here. In a real implementation, this would involve API
		log.info("Attempting to push password to device: refType={}, refId={}", refType, refId)
		return true
	}
}
