package com.beef.easytcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.pattern.LogEvent;

import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.base.buffer.PooledByteBuffer;
import com.beef.easytcp.base.handler.AbstractTcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.base.handler.SelectionKeyWrapper;
import com.beef.easytcp.base.handler.SessionObj;
import com.beef.easytcp.server.TcpException;

public class AsyncTcpClient implements ITcpClient {
	protected final static long SLEEP_PERIOD = 1;
	
	protected TcpClientConfig _config;

	protected SocketChannel _socketChannel = null;

	protected Selector _connectSelector = null;

	protected Selector _readSelector = null;
	protected SelectionKey _readKey = null;
	
	protected Selector _writeSelector = null;
	protected SelectionKey _writeKey = null;

	protected volatile long _connectBeginTime;
	protected volatile boolean _connected = false;
	
	protected ITcpEventHandlerFactory _eventHandlerFactory;
	protected volatile AbstractTcpEventHandler _eventHandler;
	
	protected ByteBuff _byteBuff;
	
	protected int _sessionId;
	
	protected ExecutorService _ioThreadPool;
	
	/**
	 * 
	 * @param host
	 * @param port
	 * @param connectTimeout in millisecond
	 */
	public AsyncTcpClient(TcpClientConfig tcpConfig, int sessionId, 
			ITcpEventHandlerFactory eventHandlerFactory) {
		_sessionId = sessionId;
		_config = tcpConfig;
		_eventHandlerFactory = eventHandlerFactory;
		
		_byteBuff = new ByteBuff(false, _config.getReceiveBufferSize());
	}
	
	public AbstractTcpEventHandler getEventHandler() {
		return _eventHandler;
	}
	
	@Override
	public void connect() throws IOException {
		_connected = false;

		//create socket
		_socketChannel = SocketChannel.open();
		_socketChannel.socket().setSoTimeout(_config.getSoTimeoutMS());

		_socketChannel.configureBlocking(false);
		
		_socketChannel.socket().setReceiveBufferSize(_config.getReceiveBufferSize());
		_socketChannel.socket().setSendBufferSize(_config.getSendBufferSize());
		
		//_socketChannel.socket().setReuseAddress(_config.isReuseAddress());
		_socketChannel.socket().setKeepAlive(_config.isKeepAlive());
		_socketChannel.socket().setTcpNoDelay(_config.isTcpNoDelay());
		
		_socketChannel.socket().setSoLinger(true, 0);
		
		_connectSelector = Selector.open();
		_socketChannel.register(
				_connectSelector, SelectionKey.OP_CONNECT 
				);
		
		_ioThreadPool = Executors.newFixedThreadPool(2);
		_ioThreadPool.execute(new ConnectThread());

		//create io selector ----------------------
		_readSelector = Selector.open();
		_writeSelector = Selector.open();
		
		//connect ------------------------------
		_connectBeginTime = System.currentTimeMillis();
		boolean connectReady = _socketChannel.connect(
				new InetSocketAddress(_config.getHost(), _config.getPort()));
		if(connectReady) {
			finishConnect();
		}
	}
	
	protected class ConnectThread implements Runnable {
		@Override
		public void run() {
			while(true) {
				try {
					boolean finishConnect = false;
					if(_connectSelector.select(_config.getConnectTimeoutMS()) != 0) {
						Set<SelectionKey> keySet = _connectSelector.selectedKeys();
						for(SelectionKey key : keySet) {
							try {
								if(!key.isValid()) {
									continue;
								}
								
								if(key.isConnectable()) {
									finishConnect();
									finishConnect = true;
								}

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
							disconnect();
							break;
						}
					}
					
					if(finishConnect) {
						break;
					}
				} catch(ClosedSelectorException e) {
					logError(e);
					break;
				} catch(Throwable e) {
					logError(e);
				} finally {
					try {
						Thread.sleep(SLEEP_PERIOD);
					} catch(InterruptedException e) {
						logInfo("ConnectThread InterruptedException -----");
						break;
					}
				}
			}
		}
	}

	protected void finishConnect() {
		try {
			final boolean connected = _socketChannel.finishConnect();
			logInfo("AsyncTcpClient connected");
			
			if(connected) {
				//io selectionKeys ------------------------------
				_readSelector.wakeup();
				_readKey = _socketChannel.register(
						_readSelector, 
						SelectionKey.OP_READ 
						);
				
				_writeSelector.wakeup();
				_writeKey = _socketChannel.register(
						_writeSelector,  
						SelectionKey.OP_WRITE
						);
				
				_ioThreadPool.execute(new ReadThread());
				

				//event handler -------------------------
				_eventHandler = _eventHandlerFactory.createHandler(
						_sessionId, 
						//_readKey, 
						_writeKey);
				//_workSelectionKey.attach(_eventHandler);
				
				_connected = true;
				_eventHandler.didConnect();
			} else {
				_connected = false;
			}
		} catch(Throwable e) {
			logError(e);
			disconnect();
		}
	}
	
	protected class ReadThread implements Runnable {
		@Override
		public void run() {
			while(true) {
				try {
					if(_readSelector.select() != 0) {
						Set<SelectionKey> keySet = _readSelector.selectedKeys();
						
						for(SelectionKey key : keySet) {
							try {
								if(!key.isValid()) {
									continue;
								}

								if(key.isReadable()) {
									handleRead(key);
								}
							} catch(CancelledKeyException e) {
								logError(e);
							} catch(Exception e) {
								logError(e);
							}
						}
						
						keySet.clear();
					}
				} catch(ClosedSelectorException e) {
					break;
				} catch(Throwable e) {
					logError(e);
				} finally {
					//Read Key.select() will block until data arrive, so no need to sleep
					try {
						Thread.sleep(SLEEP_PERIOD);
					} catch(InterruptedException e) {
						logInfo("IOThread InterruptedException -----");
						break;
					}
				}
			}
		}

		protected void handleRead(SelectionKey key) {
			SocketChannel socketChannel = (SocketChannel) key.channel();
			try {
				if(!isRealConnected()) {
					logInfo("handleRead() not connected");
					//clearSelectionKey(key);
					disconnect();
					//return false;
					return;
				} else {
					int readLen;
					_byteBuff.getByteBuffer().clear();
					readLen = socketChannel.read(_byteBuff.getByteBuffer());
					
					if(readLen > 0) {
						if(_eventHandler != null) {
							_eventHandler.didReceivedMsg(_byteBuff);
						}
					} else if(readLen < 0) {
						//mostly it is -1, and means server has disconnected
						//clearSelectionKey(key);
						logInfo("handleRead() remote peer closed");
						disconnect();
					}
					
					return;
				}
			} catch(IOException e) {
				logError(e);
				//clearSelectionKey(key);
				disconnect();
				//return false;
			} catch(Throwable e) {
				logError(e);
				//return false;
			}
		}
				
	}
	

	public int send(ByteBuffer buffer) throws TcpException {
		return _eventHandler.writeMessage(buffer);
	}
	/*
	public int send(ByteBuffer buffer) throws IOException {
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
	*/

//	protected void handleWrite(SelectionKey key) {
//	}
	

	@Override
	public void disconnect() {
		try {
			_ioThreadPool.shutdown();
		} catch(Throwable e) {
			logError(e);
		}
		try {
			closeSelector(_connectSelector);
		} catch(Throwable e) {
			logError(e);
		}
		try {
			closeSelector(_readSelector);
		} catch(Throwable e) {
			logError(e);
		}
		try {
			closeSelector(_writeSelector);
		} catch(Throwable e) {
			logError(e);
		}
	}

	@Override
	public boolean isConnected() {
		return isRealConnected()
				&& _connected
				;
	}
	
	protected boolean isRealConnected() {
		return _socketChannel.socket() != null 
				&& _socketChannel.socket().isBound() 
				&& !_socketChannel.socket().isClosed()
				&& _socketChannel.socket().isConnected() 
				&& !_socketChannel.socket().isInputShutdown()
				&& !_socketChannel.socket().isOutputShutdown()
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
			try {
				if(_eventHandler != null) {
					try {
						_eventHandler.didDisconnect();
					} catch(Throwable e) {
						logError(e);
					}
				}
			} finally {
				try {
					if(_readKey != null) {
						SocketChannelUtil.clearSelectionKey(_readKey);
					}
				} catch(Throwable e) {
					logError(e);
				}
				try {
					if(_writeKey != null) {
						SocketChannelUtil.clearSelectionKey(_writeKey);
					}
				} catch(Throwable e) {
					logError(e);
				}
				try {
					_eventHandler.destroy();
				} catch(Throwable e) {
					logError(e);
				}
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
