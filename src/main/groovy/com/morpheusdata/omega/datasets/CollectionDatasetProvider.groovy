
package com.morpheusdata.omega.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import io.reactivex.rxjava3.core.Observable
import groovy.util.logging.Slf4j

@Slf4j
class CollectionDatasetProvider extends AbstractDatasetProvider<Map, Long> {

	public static final providerName = 'Collection Dataset Provider'
	public static final providerNamespace = 'example'
	public static final providerKey = 'collectionDatasetExample'
	public static final providerDescription = 'A collection of key/value pairs'

	static members = [
			[name:'Trinity', value:2],
			[name:'Cypher', value:3],
			[name:'Apoc', value:4],
			[name:'Switch', value:5],
			[name:'Dozer', value:6],
			[name:'Tank', value:7],
			[name:'Mouse', value:8],
			[name:'Link', value:9],
			[name:'Neo', value:1]
	]

	CollectionDatasetProvider(Plugin plugin, MorpheusContext morpheus) {
		this.plugin = plugin
		this.morpheusContext = morpheus
	}

	@Override
	DatasetInfo getInfo() {
		new DatasetInfo(
				name: providerName,
				namespace: providerNamespace,
				key: providerKey,
				description: providerDescription
		)
	}

	Class<Map> getItemType() {
		return Map.class
	}

	Observable<Map> list(DatasetQuery query) {
		log.debug("list query: ${query.parameters}")
		return Observable.fromIterable(members)
	}

	Observable<Map> listOptions(DatasetQuery query) {
		log.debug("listOptions query: ${query.parameters}")
		return Observable.fromIterable(members)
	}

	Map fetchItem(Object value) {
		def rtn = null
		if(value instanceof Long) {
			rtn = item((Long)value)
		} else if(value instanceof CharSequence) {
			def longValue = value.isNumber() ? value.toLong() : null
			if(longValue) {
				rtn = item(longValue)
			}
		}
		return rtn
	}

	Map item(Long value) {
		log.debug("fetching item for value: ${value}")
		def rtn = members.find{ it.value == value }
		return rtn
	}

	String itemName(Map item) {
		return item.name
	}

	Long itemValue(Map item) {
		return (Long)item.value
	}

	@Override
	boolean isPlugin() {
		return true
	}
}
