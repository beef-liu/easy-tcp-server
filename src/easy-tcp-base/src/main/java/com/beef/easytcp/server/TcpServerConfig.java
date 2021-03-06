package com.beef.easytcp.server;

public class TcpServerConfig {
	private String host = "0.0.0.0";
	
	private int port = 60001;
	
	private int connectMaxCount = 128;
	
	private int connectTimeout = 5000;
	
	private int connectWaitCount = 64;

	private int _soTimeout = 100;
	
	private int socketIOThreadCount = 4;
	
	private int readEventThreadCount = 4;
		
	private int writeEventThreadCount = 4;
	
	//64kb
	private int socketReceiveBufferSize = 65536;
	
	private int socketSendBufferSize = 65536;
	
	private boolean tcpNoDelay = true;

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

	public int getSoTimeout() {
		return _soTimeout;
	}

	public void setSoTimeout(int soTimeout) {
		_soTimeout = soTimeout;
	}

	public int getReadEventThreadCount() {
		return readEventThreadCount;
	}

	public void setReadEventThreadCount(int readEventThreadCount) {
		this.readEventThreadCount = readEventThreadCount;
	}

	public int getWriteEventThreadCount() {
		return writeEventThreadCount;
	}

	public void setWriteEventThreadCount(int writeEventThreadCount) {
		this.writeEventThreadCount = writeEventThreadCount;
	}

	public boolean isTcpNoDelay() {
		return tcpNoDelay;
	}

	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}


}
