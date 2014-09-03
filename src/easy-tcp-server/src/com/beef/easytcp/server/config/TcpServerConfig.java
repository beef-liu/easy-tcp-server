package com.beef.easytcp.server.config;

public class TcpServerConfig {
	private String host = "0.0.0.0";
	
	private int port = 60001;
	
	private int connectMaxCount = 128;
	
	private int connectTimeout = 5000;
	
	private int connectWaitCount = 64;
	
	private int socketIOThreadCount = 4;
		
	//64kb
	private int socketReceiveBufferSize = 65536;
	
	private int socketSendBufferSize = 65536;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getConnectMaxCount() {
		return connectMaxCount;
	}

	public void setConnectMaxCount(int connectMaxCount) {
		this.connectMaxCount = connectMaxCount;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getConnectWaitCount() {
		return connectWaitCount;
	}

	public void setConnectWaitCount(int connectWaitCount) {
		this.connectWaitCount = connectWaitCount;
	}

	public int getSocketReceiveBufferSize() {
		return socketReceiveBufferSize;
	}

	public void setSocketReceiveBufferSize(int socketReceiveBufferSize) {
		this.socketReceiveBufferSize = socketReceiveBufferSize;
	}

	public int getSocketSendBufferSize() {
		return socketSendBufferSize;
	}

	public void setSocketSendBufferSize(int socketSendBufferSize) {
		this.socketSendBufferSize = socketSendBufferSize;
	}

	public int getSocketIOThreadCount() {
		return socketIOThreadCount;
	}

	public void setSocketIOThreadCount(int socketIOThreadCount) {
		this.socketIOThreadCount = socketIOThreadCount;
	}

}
