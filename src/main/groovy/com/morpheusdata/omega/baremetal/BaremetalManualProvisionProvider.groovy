package com.morpheusdata.omega.baremetal

import com.morpheusdata.PrepareHostResponse
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataOrFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.NullDataFilter
import com.morpheusdata.core.providers.HostProvisionProvider
import com.morpheusdata.model.ComputeDevice
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerInterface
import com.morpheusdata.model.ComputeServerInterfaceType
import com.morpheusdata.model.Icon
import com.morpheusdata.model.NetAddress
import com.morpheusdata.model.provisioning.HostRequest
import com.morpheusdata.response.ProvisionResponse
import com.morpheusdata.response.ServiceResponse

/**
 * This provision provider lets us "add" baremetal servers that we can provision with.
 * You can imagine this is adding servers to our inventory that are available to provision
 * with.
 */
class BaremetalManualProvisionProvider implements HostProvisionProvider, HostProvisionProvider.finalizeHostFacet {

    public static final String PROVISION_PROVIDER_CODE = 'omega.baremetal.manual-provision'
    protected MorpheusContext context
    protected Plugin plugin

    BaremetalManualProvisionProvider(Plugin plugin, MorpheusContext context) {
        this.plugin = plugin
        this.context = context
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Icon getCircularIcon() {
        return new Icon(path:'omega-circular.svg', darkPath:'omega-circular-dark.svg')
    }

    /**
     * {@inheritDoc}
     *
     * This makes it so we skip network wait since we're using stubbed servers.
     */
    @Override
    Boolean supportsAgent() {
        return false
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceResponse validateHost(ComputeServer server, Map opts) {
        return ServiceResponse.success()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceResponse<PrepareHostResponse> prepareHost(ComputeServer server, HostRequest hostRequest, Map opts) {
        return ServiceResponse.success(new PrepareHostResponse(
                computeServer: server,
                options: opts
        ))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceResponse<ProvisionResponse> runHost(ComputeServer server, HostRequest hostRequest, Map opts) {
        return ServiceResponse.success(new ProvisionResponse(installAgent: false, noAgent: true))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceResponse<ProvisionResponse> waitForHost(ComputeServer server) {
        return ServiceResponse.success(new ProvisionResponse(skipNetworkWait: true, installAgent: false, noAgent: true))
    }

    /**
     * {@inheritDoc}
     *
     * We need to pretend we have physical hardware on this server.
     */
    @Override
    ServiceResponse finalizeHost(ComputeServer server) {
        server.status = 'available'
        // Add wwpns so we can interact with alletra plugin for FC
        server.setConfigProperty("wwpns", [
            'BE:EF:CA:FE:' + (0..3).collect {
                String.format("%02X", new Random().nextInt(256))
            }.join(":")
        ])

        // Add iqns so we can interact with alletra plugin for iscsi
        server.setConfigProperty("iqns", [
            "iqn.2016-04.com.hpe:${server.name}-${server.id}".toString(),
        ])
        context.services.computeServer.save(server)

        def netInterfaces = []
        2.times { idx ->
            def prefix = "ca:fe:fe" // Common prefix for generated MACs
            def suffix = (0..2).collect {
                String.format("%02x", new Random().nextInt(256))
            }.join(":")

            def syntheticMacaddress = "${prefix}:${suffix}"
            ComputeServerInterface nic = new ComputeServerInterface(
                name: "eth${idx}",
                type: new ComputeServerInterfaceType(code: BaremetalProvisionProvider.CSI_PHYS_CODE),
                macAddress: syntheticMacaddress,
                externalId: syntheticMacaddress,
                dhcp: false,
                primaryInterface: false,
                ipMode: 'static',
            )
            def syntheticIpAddr= (0..3).collect {
                String.format("%d", new Random().nextInt(256))
            }.join(".")
            nic.addresses << new NetAddress(address: syntheticIpAddr, type: NetAddress.AddressType.IPV4)

            netInterfaces << nic
        }

        context.async.computeServer.computeServerInterface.create(netInterfaces, server).blockingGet()
        server = context.services.computeServer.get(server.id)

        // Pretend we discovered some devices on this server.
        def discoveredDevices = [
            [ name: "Generic USB", vendorId: 1, productId: 1, type: 'usb_device', domain: 0000, bus: 00, device: 14, function: 0, iommuGroup: 0 ], // generic usb
            [ name: "Generic PCI", vendorId: 1, productId: 1, type: 'pci', domain: 0000, bus: 0x0e, device: 01, function: 0, iommuGroup: 0 ], // generic pci
            [ name: "Nvidia Generic GPU", vendorId: 4318, type: 'pci', domain: 0000, bus: 0x0e, device: 02, function: 0, iommuGroup: 0 ], // Nvidia Generic GPU
            [ name: "Nvidia GeForce RTX 4090", vendorId: 4318, productId: 9860, type: 'pci', domain: 0000, bus: 0x0e, device: 13, function: 0, iommuGroup: 0 ], // Nvidia GeForce RTX 4090
            [ name: "Omega Baremetal GPU", vendorId: 1337, productId: 1337, type: 'pci', domain: 0000, bus: 0x0e, device: 14, function: 0, iommuGroup: 0 ], // Fake device type that doesn't really exist
        ]

        for (def discoveredDevice in discoveredDevices) {
            // check if we know the exact type of the device by vendorId and productId
            def type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilters(
                new DataFilter('vendorId', discoveredDevice.vendorId),
                new DataFilter('productId', discoveredDevice.productId),
            ))

            if(!type) {
                // well maybe we know who made it at least and we can pick a generic type for that vendor
                type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilters(
                    new DataFilter('vendorId', discoveredDevice.vendorId),
                    new NullDataFilter<>('productId'), // we want the generic type for this vendor, there shouldn't be a productId
                    new DataFilter('bus_type', discoveredDevice.type), // pci or usb_device
                ))
            }

            if(!type) {
                // if we don't know the type by vendorId and productId but we know what kind of device it is, pick the generic
                if (discoveredDevice.type == 'usb_device') {
                    type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilter(
                        new DataFilter('code', 'usb'),
                    ))
                } else if (discoveredDevice.type == 'pci') {
                    type = context.services.computeServer.computeDevice.type.find(new DataQuery().withFilter(
                        new DataFilter('code', 'pci'),
                    ))
                }
            }

            if (!type) {
                log.warn("Could not find a compute device type for vendorId: ${discoveredDevice.vendorId}, productId: ${discoveredDevice.productId}, type: ${discoveredDevice.type}")
                return
            }

            def computeDevice = new ComputeDevice(
                name: discoveredDevice.name,
                vendorId: discoveredDevice.vendorId,
                productId: discoveredDevice.productId,
                type: type,
                domainId: discoveredDevice.domain,
                bus: discoveredDevice.bus,
                device: discoveredDevice.device,
                functionId: discoveredDevice.function,
                iommuGroup: discoveredDevice.iommuGroup,
                server: server, // you must have a server attached.
            )
            context.services.computeServer.computeDevice.create(computeDevice)
        }

        return ServiceResponse.success()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceResponse stopServer(ComputeServer computeServer) {
        return ServiceResponse.success()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceResponse startServer(ComputeServer computeServer) {
        return ServiceResponse.success()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    MorpheusContext getMorpheus() {
        return context
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Plugin getPlugin() {
        return plugin
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getCode() {
        return PROVISION_PROVIDER_CODE
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getName() {
        return "Baremetal Stub Manual Provision"
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Boolean createDefaultInstanceType() { false }


}
