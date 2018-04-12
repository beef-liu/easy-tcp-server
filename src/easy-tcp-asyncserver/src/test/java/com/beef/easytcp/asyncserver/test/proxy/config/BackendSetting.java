package com.beef.easytcp.asyncserver.test.proxy.config;

import com.beef.easytcp.client.TcpClientConfig;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class BackendSetting {
    private TcpClientConfig _tcpClientConfig;


    public TcpClientConfig getTcpClientConfig() {
        return _tcpClientConfig;
    }

    public void setTcpClientConfig(TcpClientConfig tcpClientConfig) {
        _tcpClientConfig = tcpClientConfig;
    }
}
