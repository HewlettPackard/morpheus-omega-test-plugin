package com.morpheusdata.omega.computehost

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerType
import com.morpheusdata.model.GpuDeviceInfo
import com.morpheusdata.model.Permission
import com.morpheusdata.request.AddHostRequest
import com.morpheusdata.request.GetGpuDevicesRequest
import com.morpheusdata.request.RemoveHostRequest
import com.morpheusdata.response.GetGpuDevicesResponse
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route
import groovy.util.logging.Slf4j

/**
 * Plugin controller exposing REST endpoints to manually exercise the
 * MorpheusComputeServerService APIs.
 *
 * Endpoints (all under /plugin/compute-hosts/...):
 *   POST /plugin/compute-hosts/add     — build an AddHostRequest and call services.computeServer.addHost
 *   POST /plugin/compute-hosts/remove  — build a RemoveHostRequest and call services.computeServer.removeHost
 *   POST /plugin/compute-hosts/gpu-devices — retrieve live GPU inventory through sync and async APIs
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
                Route.build('/compute-hosts/gpu-devices', 'getGpuDevices', Permission.build('admin-appliance', 'full')),
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

    // POST /plugin/compute-hosts/gpu-devices
    def getGpuDevices(ViewModel<Map> model) {
        Map body = model.object ?: [:]
        try {
            if (body.serverId == null || body.serverId.toString().trim().isEmpty()) {
                return response([success: false, msg: 'serverId is required'], 400)
            }

            Long serverId
            try {
                serverId = Long.valueOf(body.serverId.toString())
            } catch (NumberFormatException ignored) {
                return response([success: false, msg: 'serverId must be a valid integer'], 400)
            }

            String mode = body.mode == null ? 'both' : body.mode.toString()
            if (!(mode in ['sync', 'async', 'both'])) {
                return response([success: false, msg: 'mode must be one of: sync, async, both'], 400)
            }

            ComputeServer server = morpheusContext.services.computeServer.get(serverId)
            if (!server) {
                return response([success: false, msg: "ComputeServer not found for serverId: ${serverId}"], 404)
            }

            GetGpuDevicesRequest request = new GetGpuDevicesRequest()
            request.server = server

            Map results = [:]
            if (mode in ['sync', 'both']) {
                ServiceResponse<GetGpuDevicesResponse> syncResponse =
                        morpheusContext.services.computeServer.getGpuDevices(request)
                results.sync = gpuResult(syncResponse)
            }
            if (mode in ['async', 'both']) {
                ServiceResponse<GetGpuDevicesResponse> asyncResponse =
                        morpheusContext.async.computeServer.getGpuDevices(request).blockingGet()
                results.async = gpuResult(asyncResponse)
            }

            Map responseBody = [
                    success: true,
                    server : [
                            id        : server.id,
                            name      : server.name,
                            hostname  : server.hostname,
                            externalId: server.externalId,
                            uniqueId  : server.uniqueId
                    ],
                    mode   : mode,
                    results: results
            ]
            if (mode == 'both') {
                responseBody.parity = normalizeResult(results.sync) == normalizeResult(results.async)
            }
            return JsonResponse.of(responseBody)
        } catch (Exception e) {
            log.error("Unexpected error retrieving GPU devices for serverId=${body.serverId}", e)
            return errorResponse(e.message)
        }
    }

    //Utility

    private static Map gpuResult(ServiceResponse<GetGpuDevicesResponse> result) {
        List<Map> devices = (result.data?.devices ?: []).collect { GpuDeviceInfo device ->
            [
                    uniqueId      : device.uniqueId,
                    kind          : device.kind,
                    vendor        : device.vendor,
                    name          : device.name,
                    uuid          : device.uuid,
                    pciBusId      : device.pciBusId,
                    parentUniqueId: device.parentUniqueId,
                    vmId          : device.vmId,
                    vgpuType      : device.vgpuType,
                    vgpuTypeId    : device.vgpuTypeId,
                    vendorId      : device.vendorId,
                    productId     : device.productId
            ]
        }
        return [
                success    : result.success,
                msg        : result.msg,
                errors     : result.errors,
                deviceCount: devices.size(),
                devices    : devices
        ]
    }

    private static Map normalizeResult(Map result) {
        List<Map> normalizedDevices = (result.devices ?: []).collect { Map device ->
            new LinkedHashMap(device)
        }.sort { Map left, Map right ->
            deviceSortKey(left) <=> deviceSortKey(right)
        }
        return [
                success    : result.success,
                msg        : result.msg,
                errors     : result.errors ? new TreeMap(result.errors as Map) : [:],
                deviceCount: result.deviceCount,
                devices    : normalizedDevices
        ]
    }

    private static String deviceSortKey(Map device) {
        return [
                device.uniqueId,
                device.kind,
                device.vendor,
                device.name,
                device.uuid,
                device.pciBusId,
                device.parentUniqueId,
                device.vmId,
                device.vgpuType,
                device.vgpuTypeId,
                device.vendorId,
                device.productId
        ].collect { value -> value == null ? '' : value.toString() }.join('\u0000')
    }

    private static JsonResponse response(Map body, Integer status) {
        def resp = JsonResponse.of(body)
        resp.status = status
        return resp
    }

    private static JsonResponse errorResponse(String message) {
        def resp = JsonResponse.of([success: false, msg: message])
        resp.status = 500
        return resp
    }

}
