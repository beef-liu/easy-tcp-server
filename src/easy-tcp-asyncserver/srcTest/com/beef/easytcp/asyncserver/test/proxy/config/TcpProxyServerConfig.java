package com.beef.easytcp.asyncserver.test.proxy.config;

import com.beef.easytcp.server.TcpServerConfig;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class TcpProxyServerConfig {

    private TcpServerConfig _tcpServerConfig;

    private BackendSetting _backendSetting;


    public TcpServerConfig getTcpServerConfig() {
        return _tcpServerConfig;
    }

    public void setTcpServerConfig(TcpServerConfig tcpServerConfig) {
        _tcpServerConfig = tcpServerConfig;
    }

    public BackendSetting getBackendSetting() {
        return _backendSetting;
    }

    public void setBackendSetting(BackendSetting backendSetting) {
        _backendSetting = backendSetting;
    }
}
