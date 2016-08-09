package com.beef.easytcp.asyncserver.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import com.beef.easytcp.client.ITcpClient;
import com.beef.easytcp.client.TcpClientConfig;

public class TcpClient implements ITcpClient {

	protected TcpClientConfig _config;
	protected Socket _socket;
    protected WritableByteChannel _writableChannel;
    protected ReadableByteChannel _readableChannel;
	
	
	public TcpClient(TcpClientConfig tcpConfig) {
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

            _readableChannel = Channels.newChannel(_socket.getInputStream());
            _writableChannel = Channels.newChannel(_socket.getOutputStream());
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
	
    public void send(ByteBuffer buffer) throws IOException {
    	connect();

        while(buffer.hasRemaining()) {
            _writableChannel.write(buffer);
        }
    }
    
    public void send(byte[] buffer, int offset, int len) throws IOException {
    	connect();
    	
    	_socket.getOutputStream().write(buffer, offset, len);
    	_socket.getOutputStream().flush();
    }

    public int receive(ByteBuffer buffer) throws IOException {
        connect();

        return _readableChannel.read(buffer);
    }

    public int receive(byte[] buffer, int offset, int readMaxLen) throws IOException {
    	connect();
    	
    	return _socket.getInputStream().read(buffer, offset, readMaxLen);
    }
	
}
