package com.beef.easytcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SyncTcpClient implements ITcpClient {
	protected String _host;
	protected int _port;
	protected int _timeout;
	
	protected Socket _socket;
	
	/**
	 * 
	 * @param host
	 * @param port
	 * @param connectTimeout in millisecond
	 */
	public SyncTcpClient(String host, int port, int connectTimeout) {
		_host = host;
		_port = port;
		_timeout = connectTimeout;
	}
	
	@Override
    public void connect() throws IOException {
		if (!isConnected()) {
			_socket = new Socket();
			
			_socket.setReuseAddress(true);
			_socket.setKeepAlive(true);
			_socket.setTcpNoDelay(true);
			_socket.setSoLinger(true, 0);

			_socket.connect(new InetSocketAddress(_host, _port), _timeout);
			_socket.setSoTimeout(_timeout);
		}
    }

    public boolean isConnected() {
		return _socket != null && _socket.isBound() && !_socket.isClosed()
			&& _socket.isConnected() && !_socket.isInputShutdown()
			&& !_socket.isOutputShutdown();
    }
    
    @Override
    public void disconnect() throws IOException {
		if (isConnected()) {
			IOException error = null;

	    	try {
		    	_socket.shutdownOutput();
			} catch(IOException e) {
				error = e;
			}
			
			try {
		    	_socket.shutdownInput();
			} catch(IOException e) {
				error = e;
			}
	
			try {
				if (!_socket.isClosed()) {
				    _socket.close();
				}
			} catch(IOException e) {
				error = e;
			}
			
			if(error != null) {
				throw error;
			}
		}
    }
	
    public void send(byte[] buffer, int offset, int len) throws IOException {
    	connect();
    	
    	_socket.getOutputStream().write(buffer, offset, len);
    	_socket.getOutputStream().flush();
    }
    
    public int receive(byte[] buffer, int offset, int readMaxLen) throws IOException {
    	connect();
    	
    	return _socket.getInputStream().read(buffer, offset, readMaxLen);
    }
    
}
