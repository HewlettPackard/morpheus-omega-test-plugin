package com.morpheusdata.omega.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataAndFilter
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import com.morpheusdata.model.ComputeServer
import groovy.util.logging.Slf4j
import io.reactivex.rxjava3.core.Observable

@Slf4j
class BaremetalHostsDataSetProvider extends AbstractDatasetProvider<ComputeServer, String> {
    public static final PROVIDER_NAME = "Omega Baremetal Host Dataset Provider"
    public static final PROVIDER_NAMESPACE = "com.omega"
    public static final PROVIDER_KEY = "omega-baremetal-hosts"
    public static final PROVIDER_DESCRIPTION = "Omega Baremetal Hosts Dataset Provider"

    BaremetalHostsDataSetProvider(Plugin plugin, MorpheusContext morpheus) {
        this.plugin = plugin
        this.morpheusContext = morpheus
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

    @Override
    Class<ComputeServer> getItemType() {
        return ComputeServer.class
    }

    @Override
    Observable<ComputeServer> list(DatasetQuery query) {
        log.info(query.parameters.toString())
        Long accountId = query.get("accountId")?.toLong()
        Long planId = query.get('plan.id')?.toLong()
        def resourcePoolId = query.get('config.resourcePoolId') as String
        def phrase = query.get('phrase') as String
        // Core is passing the resource pool id as "pool-123" so we need to split it.
        resourcePoolId = resourcePoolId ? resourcePoolId.split('-')[1]?.toLong() : null

        def listQuery = new DataQuery()
                .withFilters(
                        new DataAndFilter(
                                new DataFilter('account.id', accountId),
                                new DataFilter('plan.id', planId),
                                new DataFilter('status', 'available')
                        ),
                ).withSort('name')

        if (resourcePoolId) {
            listQuery.withFilters(
                    new DataFilter('resourcePool.id', resourcePoolId)
            )
        }
        
        if (phrase) {
            listQuery.withFilters(
                    new DataFilter('name', '=~', phrase)
            )
        }

        return morpheusContext.async.computeServer.list(listQuery)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Observable<Map> listOptions(DatasetQuery query) {
        list(query).map { [name: it.name, value: it.id] }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    ComputeServer fetchItem(Object value) {
        def rtn = null
        if (value instanceof String) {
            rtn = item((String) value)
        }
        return rtn
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ComputeServer item(String value) {
        def query = new DatasetQuery().withFilter("id", value)
        query.max = 1
        return list(query as DatasetQuery)
                .toList()
                .blockingGet()
                .first()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String itemName(ComputeServer item) {
        return item.name
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String itemValue(ComputeServer item) {
        return item.id
    }

    /**
     * {@inheritDoc}
     */
    @Override
    boolean isPlugin() {
        return true
    }
}
