package com.beef.easytcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SyncTcpClient implements ITcpClient {
	protected TcpClientConfig _config;
	protected Socket _socket;
	
	/**
	 * 
	 * @param host
	 * @param port
	 * @param connectTimeout in millisecond
	 */
	public SyncTcpClient(TcpClientConfig tcpConfig) {
		_config = tcpConfig;
	}
	
	@Override
    public void connect() throws IOException {
		if (!isConnected()) {
			_socket = new Socket();
			
			_socket.setReuseAddress(_config.isReuseAddress());
			_socket.setKeepAlive(_config.isKeepAlive());
			_socket.setTcpNoDelay(_config.isTcpNoDelay());
			
			_socket.setSoLinger(true, 0);
			
			_socket.setReceiveBufferSize(_config.getReceiveBufferSize());
			_socket.setSendBufferSize(_config.getSendBufferSize());
			
			_socket.setSoTimeout(_config.getSoTimeoutMS());
			
			_socket.connect(
					new InetSocketAddress(_config.getHost(), _config.getPort()), 
					_config.getConnectTimeoutMS());
		}
    }

	public Socket getSocket() {
		return _socket;
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

    public int receiveUntilFillUpBufferOrEnd(byte[] buffer, int offset, int readMaxLen) throws IOException {
    	connect();
    	
    	int readTotalLen = 0;
    	int readLen;
    	int position = offset;
    	int remaining = readMaxLen;
    	while(true) {
    		try {
        		readLen = _socket.getInputStream().read(buffer, position, remaining);
    		} catch(SocketTimeoutException e) {
    			//nothing to read
    			break;
    		}
    		
    		if(readLen < 0) {
    			if(readTotalLen == 0) {
    				return -1;
    			}
    			
    			break;
    		}
    		
    		if(readLen > 0) {
    			remaining -= readLen;
    			position += readLen;
    			readTotalLen += readLen;
    			
    			if(remaining == 0) {
    				break;
    			}
    		}
    	}
    	
    	return readTotalLen;
    }
    
}
