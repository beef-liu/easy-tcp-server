package com.beef.easytcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class AsyncTcpClient implements ITcpClient, Runnable {
	protected String _host;
	protected int _port;
	protected int _timeout;
	
	protected int _socketSendBufferSize;
	protected int _socketReceiveBufferSize;

	protected SocketChannel _socketChannel = null;
	protected Selector _workSelector = null;
	protected SelectionKey _workSelectionKey = null;
	
	private volatile boolean _connected = false;
	
	/**
	 * 
	 * @param host
	 * @param port
	 * @param connectTimeout in millisecond
	 */
	public AsyncTcpClient(String host, int port, int connectTimeout, 
			int socketSendBufferSize, int socketReceiveBufferSize) {
		_host = host;
		_port = port;
		_timeout = connectTimeout;
		_socketSendBufferSize = socketSendBufferSize;
		_socketReceiveBufferSize = socketReceiveBufferSize;
	}
	
	@Override
	public void connect() throws IOException {
		_connected = false;
		
		//create selector
		_workSelector = Selector.open();
		
		//create socket
		_socketChannel = SocketChannel.open();
		_socketChannel.configureBlocking(false);
		_socketChannel.socket().setReceiveBufferSize(_socketReceiveBufferSize);
		_socketChannel.socket().setSendBufferSize(_socketSendBufferSize);
		_socketChannel.socket().setSoTimeout(_timeout);
		
		_socketChannel.socket().setReuseAddress(true);
		_socketChannel.socket().setKeepAlive(true);
		_socketChannel.socket().setTcpNoDelay(true);
		_socketChannel.socket().setSoLinger(true, 0);
		
		_workSelectionKey = _socketChannel.register(
				_workSelector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		
		//connect
		boolean connectReady = _socketChannel.connect(new InetSocketAddress(_host, _port));
		if(connectReady) {
			finishConnect();
		}
	}

	@Override
	public void run() {
		try {
			
		} catch(Throwable e) {
			logError(e);
		}
	}
	
	protected void finishConnect() throws IOException {
		_connected = _socketChannel.finishConnect();
	}
	

	@Override
	public void disconnect() throws IOException {
		clearSelectionKey(_workSelectionKey);
	}

	@Override
	public boolean isConnected() {
		return _socketChannel.socket() != null 
				&& _socketChannel.socket().isBound() 
				&& !_socketChannel.socket().isClosed()
				&& _socketChannel.socket().isConnected() 
				&& !_socketChannel.socket().isInputShutdown()
				&& !_socketChannel.socket().isOutputShutdown()
				&& _connected
				;
	}

	protected void logError(Throwable e) {
		e.printStackTrace();
	}
	
	protected static void clearSelectionKey(SelectionKey key) {
		try {
			if(key != null) {
				SocketChannel socketChannel = (SocketChannel) key.channel();
				closeSocketChannel(socketChannel);
				logger.info("close socketChannel");
				
				key.cancel();
			}
		} catch(Exception e) {
			logger.error("clearSelectionKey()", e);
		}
	}

	protected static void closeSocketChannel(SocketChannel socketChannel) {
		try {
			if(!socketChannel.socket().isInputShutdown()) {
				socketChannel.socket().shutdownInput();
			}
		} catch(Exception e) {
		}
		
		try {
			if(socketChannel.socket().isOutputShutdown()) {
				socketChannel.socket().shutdownOutput();
			}
		} catch(Exception e) {
		}
		
		try {
			if(socketChannel.socket().isClosed()) {
				socketChannel.socket().close();
			}
		} catch(Exception e) {
		}

		try {
			socketChannel.close();
		} catch(Exception e) {
		}
	}
	
}
