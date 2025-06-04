package com.morpheusdata.omega.process

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import io.reactivex.rxjava3.core.Observable

class ProcessServiceExamplesDataSource extends AbstractDatasetProvider<ProcessExample, String> {
	public static final PROVIDER_NAME = "Process Service Example Type"
	public static final PROVIDER_NAMESPACE = "omega.process"
	public static final PROVIDER_KEY = "process-service-examples"
	public static final PROVIDER_DESCRIPTION = "Process Service Examples"

	ProcessServiceExamplesDataSource(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	DatasetInfo getInfo() {
		new DatasetInfo(
				name: PROVIDER_NAME,
				namespace: PROVIDER_NAMESPACE,
				key: PROVIDER_KEY,
				description: PROVIDER_DESCRIPTION,
		)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Class<ProcessExample> getItemType() {
		return ProcessExample.class
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Observable<ProcessExample> list(DatasetQuery query) {
		Observable.fromArray(ProcessExample.values())
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Observable<Map> listOptions(DatasetQuery query) {
		Observable.fromArray(ProcessExample.values()).map {
			[name: it.getName(), value: it.toString()]
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ProcessExample fetchItem(Object value) {
		return ProcessExample.valueOf(value.toString())
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ProcessExample item(String value) {
		return ProcessExample.valueOf(value)
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String itemName(ProcessExample item) {
		return item.getName()
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String itemValue(ProcessExample item) {
		return item.toString()
	}

	enum ProcessExample {
		BASIC("Basic Example"),
		BASIC_CUSTOM("Basic Example With Custom Step"),
		UPDATE("Update Example"),
		FAIL("Failed Step Example"),
		FAIL_UNENDED("Fail with Unended Step Example"),
		FAIL_EXCEPTION("Fail with Exception Example"),
		MULTI("Multi Step Example"),
		FULL("All Functionality Example"),
		NOOP("Noop Example");

		private final String name

		ProcessExample(String name) {
			this.name = name
		}

		String getName() {
			return name
		}
	}
}
