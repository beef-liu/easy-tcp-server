package com.beef.easytcp.client;

public class TcpClientConfig {
	private String _host = "127.0.0.1";
	
	private int _port;

	private boolean _reuseAddress = true;
	
	private boolean _keepAlive = true;
	
	private boolean _tcpNoDelay = true;
		
	private int _soTimeoutMS = 100;
	
	private int _connectTimeoutMS = 1000;
	
	private int _receiveBufferSize = 1024 * 4;
	
	private int _sendBufferSize = 1024 * 4;

	public String getHost() {
		return _host;
	}

	public void setHost(String host) {
		_host = host;
	}

	public int getPort() {
		return _port;
	}

	public void setPort(int port) {
		_port = port;
	}

	public boolean isReuseAddress() {
		return _reuseAddress;
	}

	public void setReuseAddress(boolean reuseAddress) {
		_reuseAddress = reuseAddress;
	}

	public boolean isKeepAlive() {
		return _keepAlive;
	}

	public void setKeepAlive(boolean keepAlive) {
		_keepAlive = keepAlive;
	}

	public boolean isTcpNoDelay() {
		return _tcpNoDelay;
	}

	public void setTcpNoDelay(boolean tcpNoDelay) {
		_tcpNoDelay = tcpNoDelay;
	}

	public int getSoTimeoutMS() {
		return _soTimeoutMS;
	}

	public void setSoTimeoutMS(int soTimeoutMS) {
		_soTimeoutMS = soTimeoutMS;
	}

	public int getReceiveBufferSize() {
		return _receiveBufferSize;
	}

	public void setReceiveBufferSize(int receiveBufferSize) {
		_receiveBufferSize = receiveBufferSize;
	}

	public int getSendBufferSize() {
		return _sendBufferSize;
	}

	public void setSendBufferSize(int sendBufferSize) {
		_sendBufferSize = sendBufferSize;
	}

	public int getConnectTimeoutMS() {
		return _connectTimeoutMS;
	}

	public void setConnectTimeoutMS(int connectTimeoutMS) {
		_connectTimeoutMS = connectTimeoutMS;
	}
	
	
}
