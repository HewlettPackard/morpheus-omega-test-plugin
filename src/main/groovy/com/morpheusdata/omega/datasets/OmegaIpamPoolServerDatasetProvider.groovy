package com.morpheusdata.omega.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import com.morpheusdata.model.NetworkPoolServer
import com.morpheusdata.omega.ipam.OmegaIpamProvider
import io.reactivex.rxjava3.core.Observable

class OmegaIpamPoolServerDatasetProvider extends AbstractDatasetProvider<NetworkPoolServer, Long> {
	public static final String PROVIDER_NAME = 'Omega IPAM Pool Server Dataset Provider'
	public static final String PROVIDER_NAMESPACE = 'com.omega.ipam'
	public static final String PROVIDER_KEY = 'omega-ipam-pool-servers'
	public static final String PROVIDER_DESCRIPTION = 'Omega IPAM pool server selector'

	OmegaIpamPoolServerDatasetProvider(Plugin plugin, MorpheusContext morpheus) {
		this.plugin = plugin
		this.morpheusContext = morpheus
	}

	@Override
	DatasetInfo getInfo() {
		new DatasetInfo(
			name: PROVIDER_NAME,
			namespace: PROVIDER_NAMESPACE,
			key: PROVIDER_KEY,
			description: PROVIDER_DESCRIPTION
		)
	}

	@Override
	Class<NetworkPoolServer> getItemType() {
		return NetworkPoolServer.class
	}

	@Override
	Observable<NetworkPoolServer> list(DatasetQuery query) {
		def listQuery = new DataQuery(query.user, query.parameters)
		listQuery.withFilter('type.code', OmegaIpamProvider.IPAM_PROVIDER_CODE)
		if(!listQuery.sort) {
			listQuery.sort = 'name'
		}
		return morpheusContext.async.network.poolServer.list(listQuery)
	}

	@Override
	Observable<Map> listOptions(DatasetQuery query) {
		return list(query).map { [name: it.name, value: it.id] }
	}

	@Override
	NetworkPoolServer fetchItem(Object value) {
		def longValue = value instanceof Number ? value.toLong() : value?.toString()?.isLong() ? value.toString().toLong() : null
		return longValue != null ? item(longValue) : null
	}

	@Override
	NetworkPoolServer item(Long value) {
		def query = new DataQuery().withFilter('id', value).withFilter('type.code', OmegaIpamProvider.IPAM_PROVIDER_CODE)
		query.max = 1
		return morpheusContext.services.network.poolServer.list(query)?.find()
	}

	@Override
	String itemName(NetworkPoolServer item) {
		return item.name
	}

	@Override
	Long itemValue(NetworkPoolServer item) {
		return item.id
	}
}
