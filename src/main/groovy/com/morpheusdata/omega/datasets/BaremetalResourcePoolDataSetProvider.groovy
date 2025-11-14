package com.morpheusdata.omega.datasets

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataAndFilter
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.DatasetInfo
import com.morpheusdata.core.data.DatasetQuery
import com.morpheusdata.core.providers.AbstractDatasetProvider
import com.morpheusdata.model.CloudPool
import io.reactivex.rxjava3.core.Observable

class BaremetalResourcePoolDataSetProvider extends AbstractDatasetProvider<CloudPool, String> {
    public static final PROVIDER_NAME = "Omega Baremetal Cloud Pool Provider"
    public static final PROVIDER_NAMESPACE = "com.Omega.ilo"
    public static final PROVIDER_KEY = "omega-cloud-pool"
    public static final PROVIDER_DESCRIPTION = "Omega Baremetal cloud pools"

    BaremetalResourcePoolDataSetProvider(Plugin plugin, MorpheusContext morpheusContext) {
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
    Class<CloudPool> getItemType() {
        CloudPool.class
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Observable<CloudPool> list(DatasetQuery query) {
        Long cloudId = query.get("zoneId")?.toLong()
        Long accountId = query.get("accountId")?.toLong()
        def listQuery = new DataQuery()
                .withFilters(
                        new DataAndFilter(
                                new DataFilter('owner.id', accountId),
                        ),
                        new DataAndFilter(
                                new DataFilter('active', true),
                                new DataFilter('status', 'available'),
                        ),
                        new DataAndFilter(
                                new DataFilter('refType', 'ComputeZone'),
                                new DataFilter('refId', cloudId),
                        )
                ).withSort('name')

        morpheusContext.async.cloud.pool.list(listQuery)
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
    CloudPool fetchItem(Object value) {
        def rtn = null
        if (value instanceof String) {
            rtn = item((String) value)
        }
        rtn
    }

    /**
     * {@inheritDoc}
     */
    @Override
    CloudPool item(String value) {
        def query = new DatasetQuery().withFilter("id", value)
        query.max = 1
        list(query as DatasetQuery)
                .toList()
                .blockingGet()
                .first()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String itemName(CloudPool item) {
        return item.name
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String itemValue(CloudPool item) {
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


