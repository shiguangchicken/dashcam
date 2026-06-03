package com.firmmy.dashcam.core.network;

import android.net.nsd.NsdServiceInfo;

final class NsdServiceInfoFactory {
    private NsdServiceInfoFactory() {
    }

    static NsdServiceInfo create(String serviceType, String serviceName, int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceType(serviceType);
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setPort(port);
        return serviceInfo;
    }
}
