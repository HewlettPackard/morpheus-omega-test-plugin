package com.morpheusdata.omega.computehost

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.CloudPool
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerType
import com.morpheusdata.model.Permission
import com.morpheusdata.model.ServicePlan
import com.morpheusdata.request.AddHostRequest
import com.morpheusdata.request.RemoveHostRequest
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route
import groovy.util.logging.Slf4j

/**
 * Plugin controller exposing REST endpoints to manually exercise the
 * MorpheusComputeServerService addHost / removeHost context APIs (MORPH-12852).
 *
 * Endpoints (all under /plugin/compute-hosts/...):
 *   POST /plugin/compute-hosts/add     — build an AddHostRequest and call services.computeServer.addHost
 *   POST /plugin/compute-hosts/remove  — build a RemoveHostRequest and call services.computeServer.removeHost
 *
 * @since 0.4.0
 */
@Slf4j
class OmegaComputeHostController implements PluginController {

    private MorpheusContext morpheusContext
    private Plugin plugin

    OmegaComputeHostController(Plugin plugin, MorpheusContext morpheusContext) {
        this.plugin = plugin
        this.morpheusContext = morpheusContext
    }

    List<Route> getRoutes() {
        return [
                Route.build('/compute-hosts/add', 'add', Permission.build('admin-appliance', 'full')),
                Route.build('/compute-hosts/remove', 'remove', Permission.build('admin-appliance', 'full')),
        ]
    }

    MorpheusContext getMorpheus() {
        return morpheusContext
    }

    Plugin getPlugin() {
        return plugin
    }

    String getCode() {
        return 'omega-compute-host-controller'
    }

    @Override
    String getName() {
        return 'Omega Compute Host Controller'
    }

    // POST /plugin/compute-hosts/add
    def add(ViewModel<Map> model) {
        try {
            Map body = model.object ?: [:]
            Long cloudId = body.cloudId as Long
            if (!cloudId) {
                def resp = JsonResponse.of([success: false, msg: "cloudId is required"])
                resp.status = 400
                return resp
            }
            Cloud cloud = morpheusContext.services.cloud.get(cloudId)
            if (!cloud) {
                def resp = JsonResponse.of([success: false, msg: "Cloud not found for cloudId: ${cloudId}"])
                resp.status = 404
                return resp
            }
            // Resolve the ComputeServerType from the cloud's available host types
            Collection<ComputeServerType> serverTypes =
                    morpheusContext.async.cloud.getComputeServerTypes(cloud.id).blockingGet()
            Long serverTypeId = body.serverTypeId as Long
            String serverTypeCode = body.serverTypeCode as String
            ComputeServerType serverType = serverTypes?.find { type ->
                (serverTypeId && type.id == serverTypeId) || (serverTypeCode && type.code == serverTypeCode)
            }
            if (!serverType) {
                def resp = JsonResponse.of([
                        success       : false,
                        msg           : "ComputeServerType not found on cloud ${cloudId} for serverTypeId=${serverTypeId} / serverTypeCode=${serverTypeCode}",
                        availableTypes: serverTypes?.collect { [id: it.id, code: it.code, name: it.name] }
                ])
                resp.status = 404
                return resp
            }

            AddHostRequest request = new AddHostRequest()
            request.serverType = serverType
            request.serverName = body.serverName as String
            request.hostname = body.hostname as String
            request.siteId = body.siteId as Long
            request.licenseCheck = body.containsKey("licenseCheck") ? (body.licenseCheck as boolean) : true
            if (body.config instanceof Map) {
                request.config = body.config as Map
            }
            if (body.planId != null) {
                request.plan = morpheusContext.services.servicePlan.get(body.planId as Long)
            }
            if (body.poolId != null) {
                request.pool = morpheusContext.services.cloud.pool.get(body.poolId as Long)
            }

            ServiceResponse result = morpheusContext.services.computeServer.addHost(cloud, request)
            return JsonResponse.of([
                    success: result.success,
                    msg    : result.msg,
                    errors : result.errors,
                    data   : result.data ? [id: result.data.id, name: result.data.name] : null
            ])
        } catch (e) {
            return errorResponse(e.message)
        }
    }

    // POST /plugin/compute-hosts/remove
    def remove(ViewModel<Map> model) {
        try {
            Map body = model.object ?: [:]
            Long serverId = body.serverId as Long
            if (!serverId) {
                def resp = JsonResponse.of([success: false, msg: "serverId is required"])
                resp.status = 400
                return resp
            }
            ComputeServer server = morpheusContext.services.computeServer.get(serverId)
            if (!server) {
                def resp = JsonResponse.of([success: false, msg: "ComputeServer not found for serverId: ${serverId}"])
                resp.status = 404
                return resp
            }

            RemoveHostRequest request = new RemoveHostRequest()
            request.force = body.force as boolean
            request.removeResources = body.removeResources as boolean
            request.removeInstances = body.removeInstances as boolean
            request.skipPolicyCheck = body.skipPolicyCheck as boolean
            if (body.userId != null) {
                request.userId = body.userId as Long
            }

            ServiceResponse result = morpheusContext.services.computeServer.removeHost(server, request)
            return JsonResponse.of([
                    success: result.success,
                    msg: result.msg,
                    errors: result.errors,
                    data: result.data
            ])
        } catch (e) {
            return errorResponse(e.message)
        }
    }

    //Utility

    private static JsonResponse errorResponse(String message) {
        def resp = JsonResponse.of([success: false, msg: message])
        resp.status = 500
        return resp
    }

}
