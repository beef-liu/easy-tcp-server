package com.beef.easytcp.asyncserver.test.proxy.config;

import java.util.List;

import com.beef.easytcp.server.TcpServerConfig;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class TcpProxyServerConfig {

    private TcpServerConfig _tcpServerConfig;

    //private BackendSetting _backendSetting;
    private List<BackendSetting> _backendList;


    public TcpServerConfig getTcpServerConfig() {
        return _tcpServerConfig;
    }

    public void setTcpServerConfig(TcpServerConfig tcpServerConfig) {
        _tcpServerConfig = tcpServerConfig;
    }

	public List<BackendSetting> getBackendList() {
		return _backendList;
	}

	public void setBackendList(List<BackendSetting> backendList) {
		_backendList = backendList;
	}
    
}
