package com.beef.easytcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;

import org.apache.log4j.pattern.LogEvent;

import com.beef.easytcp.ByteBuff;
import com.beef.easytcp.ITcpEventHandler;
import com.beef.easytcp.MessageList;
import com.beef.easytcp.SelectionKeyWrapper;
import com.beef.easytcp.SessionObj;
import com.beef.easytcp.SocketChannelUtil;
import com.beef.easytcp.server.buffer.PooledByteBuffer;

public class AsyncTcpClient implements ITcpClient, Runnable {
	protected TcpClientConfig _config;

	protected SocketChannel _socketChannel = null;
	protected Selector _workSelector = null;
	protected SelectionKey _workSelectionKey = null;

	protected volatile long _connectBeginTime;
	protected volatile boolean _connected = false;
	
	protected ITcpEventHandler _eventHandler;
	
	protected ByteBuff _byteBuff;
	
	/**
	 * 
	 * @param host
	 * @param port
	 * @param connectTimeout in millisecond
	 */
	public AsyncTcpClient(TcpClientConfig tcpConfig) {
		_config = tcpConfig;
		
		_byteBuff = new ByteBuff(false, _config.getReceiveBufferSize());
	}
	
	public void setEventHandler(ITcpEventHandler eventHandler) {
		if(_eventHandler != eventHandler) {
			_eventHandler = null;
			_eventHandler = eventHandler;
		}
	}
	
	@Override
	public void connect() throws IOException {
		_connected = false;
		
		//create selector
		_workSelector = Selector.open();
		
		//create socket
		_socketChannel = SocketChannel.open();
		_socketChannel.configureBlocking(false);
		
		_socketChannel.socket().setReceiveBufferSize(_config.getReceiveBufferSize());
		_socketChannel.socket().setSendBufferSize(_config.getSendBufferSize());
		_socketChannel.socket().setSoTimeout(_config.getSoTimeoutMS());
		
		_socketChannel.socket().setReuseAddress(_config.isReuseAddress());
		_socketChannel.socket().setKeepAlive(_config.isKeepAlive());
		_socketChannel.socket().setTcpNoDelay(_config.isTcpNoDelay());
		
		_socketChannel.socket().setSoLinger(true, 0);
		
		_workSelectionKey = _socketChannel.register(
				_workSelector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		
		//connect
		_connectBeginTime = System.currentTimeMillis();
		boolean connectReady = _socketChannel.connect(
				new InetSocketAddress(_config.getHost(), _config.getPort()));
		if(connectReady) {
			finishConnect();
		}
	}

	@Override
	public void run() {
		try {
			int availableKeyCount = _workSelector.select(_config.getConnectTimeoutMS()); 
			if(availableKeyCount != 0) {
				Set<SelectionKey> keySet = _workSelector.selectedKeys();
				
				for(SelectionKey key : keySet) {
					try {
						if(!key.isValid()) {
							continue;
						}
						
						if(key.isConnectable()) {
							finishConnect();
						}

						if(key.isReadable()) {
							handleRead(key);
						}
						
//						if(key.isWritable()) {
//							handleWrite(key);
//						}
					} catch(CancelledKeyException e) {
						logError(e);
					} catch(Exception e) {
						logError(e);
					}
				}
				
				keySet.clear();
			} 
			
			if(_socketChannel.isConnectionPending()) {
				if((System.currentTimeMillis() - _connectBeginTime) >= _config.getConnectTimeoutMS()) {
					logInfo("Connecting time out");
				}
			}
		} catch(Throwable e) {
			logError(e);
		}
	}
	
	protected void handleRead(SelectionKey key) {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		try {
			if(!isConnected()) {
				clearSelectionKey(key);
				//return false;
				return;
			} else {
				int readLen;
				_byteBuff.getByteBuffer().clear();
				readLen = socketChannel.read(_byteBuff.getByteBuffer());
				
				if(readLen > 0) {
					if(_eventHandler != null) {
						_eventHandler.didReceivedMsg(new SelectionKeyWrapper(key), _byteBuff);
					}
				} else if(readLen < 0) {
					//mostly it is -1, and means server has disconnected
					clearSelectionKey(key);
				}
				
				return;
			}
		} catch(IOException e) {
			clearSelectionKey(key);
			//return false;
		} catch(Throwable e) {
			logError(e);
			//return false;
		}
	}
	
	public int sendMsg(ByteBuffer buffer) throws IOException {
		if(_workSelectionKey.isValid() 
				&& _workSelectionKey.isWritable()) {
			if(!isConnected()) {
				clearSelectionKey(_workSelectionKey);
				return 0;
			} else {
				return _socketChannel.write(buffer);
			}
		} else {
			return 0;
		}
	}

//	protected void handleWrite(SelectionKey key) {
//	}
	
	protected void finishConnect() throws IOException {
		_connected = _socketChannel.finishConnect();
		
		if(_eventHandler != null) {
			_eventHandler.didConnect(new SelectionKeyWrapper(_workSelectionKey));
		}
	}
	

	@Override
	public void disconnect() throws IOException {
		closeSelector(_workSelector);
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

	protected static void logInfo(String msg) {
		System.out.println(msg);
	}
	
	protected static void logError(Throwable e) {
		e.printStackTrace();
	}

	protected void clearSelectionKey(SelectionKey selectionKey) {
		if(SocketChannelUtil.clearSelectionKey(selectionKey)) {
			if(_eventHandler != null) {
				_eventHandler.didDisconnect(new SelectionKeyWrapper(selectionKey));
			}
		}
	}
	
	protected static void closeSelector(Selector selector) throws IOException {
		Set<SelectionKey> keySet = selector.selectedKeys();
		
		for(SelectionKey key : keySet) {
			if(!key.isValid()) {
				continue;
			}

			SocketChannelUtil.clearSelectionKey(key);
		}
		
		selector.close();
	}
	
}
