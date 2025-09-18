/*
* Copyright 2022 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.morpheusdata.omega

import com.morpheusdata.omega.addon.AddonPackageTestClusterTabProvider
import com.morpheusdata.omega.addon.AddonPackageTypeProvider
import com.morpheusdata.omega.baremetal.BaremetalCloudProvider
import com.morpheusdata.omega.baremetal.BaremetalProvisionProvider
import com.morpheusdata.omega.datastore.DatastoreProvider
import com.morpheusdata.omega.event.EventClusterSubscribingIntegration
import com.morpheusdata.omega.event.EventGlobalSubscribingProvider
import com.morpheusdata.omega.process.ProcessServiceComputeTypePackageProvider
import com.morpheusdata.omega.process.ProcessServiceExampleCloudProvider
import com.morpheusdata.omega.process.ProcessServiceExampleProvisionProvider
import com.morpheusdata.omega.process.ProcessServiceExamplesDataSource

import com.morpheusdata.omega.storageserver.StorageServerProvider
import com.morpheusdata.core.Plugin

@SuppressWarnings('unused')
class MorpheusOmegaTestPlugin extends Plugin {

	@Override
	String getCode() {
		return 'morpheus-omega-test'
	}

	@Override
	void initialize() {
		this.setName("Morpheus Omega Test")

		this.registerProvider(new ProcessServiceExampleCloudProvider(this, this.morpheus))
		this.registerProvider(new ProcessServiceExampleProvisionProvider(this, this.morpheus))
		this.registerProvider(new ProcessServiceExamplesDataSource(this, this.morpheus))
		this.registerProvider(new ProcessServiceComputeTypePackageProvider(this, this.morpheus))

		this.registerProvider(new BaremetalCloudProvider(this,this.morpheus))
		this.registerProvider(new BaremetalProvisionProvider(this,this.morpheus))

		this.registerProvider(new AddonPackageTestClusterTabProvider(this,this.morpheus))
		this.registerProvider(new AddonPackageTypeProvider(this,this.morpheus))

		this.registerProvider(new StorageServerProvider(this,this.morpheus))

		this.registerProvider(new DatastoreProvider(this, this.morpheus))

		this.registerProvider(new EventGlobalSubscribingProvider(this,this.morpheus))
		this.registerProvider(new EventClusterSubscribingIntegration(this,this.morpheus))
	}

	/**
	 * Called when a plugin is being removed from the plugin manager (aka Uninstalled)
	 */
	@Override
	void onDestroy() {
		//nothing to do for now
	}
}
