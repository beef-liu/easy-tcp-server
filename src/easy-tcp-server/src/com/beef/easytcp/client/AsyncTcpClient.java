package com.beef.easytcp.client;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.State;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.FileChannel;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.IPool;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.buffer.PooledByteBuffer;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.base.handler.TcpReadEvent;
import com.beef.easytcp.base.handler.TcpWriteEvent;
import com.beef.easytcp.base.handler.TcpWriteEventThread;
import com.beef.easytcp.util.thread.TaskLoopThread;

public class AsyncTcpClient implements ITcpClient {	
	private final static Logger logger = Logger.getLogger(AsyncTcpClient.class);

	protected final static long SLEEP_PERIOD = 1;
	
	protected final TcpClientConfig _config;
//	protected ByteBuff _byteBuffForRead;
//	protected ByteBuff _byteBuffForWrite;
	
	//protected final ByteBufferPool _bufferPool;
	protected final IPool<PooledByteBuffer> _bufferPool;
	
	protected final boolean _connectInSyncMode;
	
	protected CountDownLatch _connectLatch;

	protected SocketChannel _socketChannel = null;

	protected Selector _connectSelector = null;

	protected Selector _readSelector = null;
	protected SelectionKey _readKey = null;
	
	protected Selector _writeSelector = null;
	protected SelectionKey _writeKey = null;

	protected volatile long _connectBeginTime;
	//protected volatile boolean _connected = false;
	protected AtomicBoolean _connected = new AtomicBoolean(false);
	
	//protected ITcpEventHandlerFactory _eventHandlerFactory;
	protected volatile ITcpEventHandler _eventHandler;
	
	protected int _sessionId = 0;
	
	protected ExecutorService _ioThreadPool;
	protected TaskLoopThread<TcpReadEvent> _readEventThread;
	protected TcpWriteEventThread _writeEventThread;

	public int getSessionId() {
		return _sessionId;
	}
	
	public SelectionKey getWriteKey() {
		return _writeKey;
	}
	
	public AsyncTcpClient(
			TcpClientConfig tcpConfig, 
			//int sessionId, 
			//ITcpEventHandlerFactory eventHandlerFactory,
			//int byteBufferPoolSize
			//ByteBufferPool byteBufferPool
			IPool<PooledByteBuffer> byteBufferPool
			) {
		this(tcpConfig, byteBufferPool, true);
	}
	
	public AsyncTcpClient(
			TcpClientConfig tcpConfig, 
			//int sessionId, 
			//ITcpEventHandlerFactory eventHandlerFactory,
			//int byteBufferPoolSize
			//ByteBufferPool byteBufferPool,
			IPool<PooledByteBuffer> byteBufferPool,
			boolean connectInSyncMode
			) {
		//_sessionId = sessionId;
		_config = tcpConfig;
		//_eventHandlerFactory = eventHandlerFactory;
		
//		_byteBuffForRead = new ByteBuff(false, _config.getReceiveBufferSize());
//		_byteBuffForWrite = new ByteBuff(false, _config.getReceiveBufferSize());
		
		/*
		boolean isAllocateDirect = false;
		GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
		byteBufferPoolConfig.setMaxIdle(byteBufferPoolSize);
		byteBufferPoolConfig.setMaxTotal(byteBufferPoolSize);
		byteBufferPoolConfig.setMaxWaitMillis(10);
		
		_bufferPool = new ByteBufferPool(
				byteBufferPoolConfig, isAllocateDirect, _config.getReceiveBufferSize());
		*/
		_bufferPool = byteBufferPool;
		
		_connectInSyncMode = connectInSyncMode;
	}
	
	public void setEventHandler(ITcpEventHandler eventHandler) {
		_eventHandler = eventHandler;
	}

	public ITcpEventHandler getEventHandler() {
		return _eventHandler;
	}
	
	@Override
	public void connect() throws IOException {
		if(isConnected()) {
			//logInfo("connect() isConnected() true");
			return;
		}

		try {
			synchronized (this) {
				if(_connectLatch != null && _connectLatch.getCount() == 1) {
					//in connecting
					return;
				}
				_connectLatch = new CountDownLatch(1);
				
				if(_socketChannel != null && _socketChannel.isConnectionPending()) {
					//in connecting
					return;
				}
			}
			
			//_connected = false;
			_connected.compareAndSet(true, false);

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
			
			_ioThreadPool = Executors.newCachedThreadPool();
			_ioThreadPool.execute(new ConnectThread());

			//connect ------------------------------
			
			_connectBeginTime = System.currentTimeMillis();
			boolean connectReady = _socketChannel.connect(
					new InetSocketAddress(_config.getHost(), _config.getPort()));
			if(connectReady) {
				finishConnect();
			}
		} finally {
			if(_connectInSyncMode) {
				waitConnect(_config.getConnectTimeoutMS() * 2);
			}
		}
		
	}
	
	protected boolean waitConnect(long timeout) {
		/*
		final int sleepTime = 10;
		int waitTime = 0;
		while(waitTime <= timeout) {
			if(isConnected()) {
				break;
			}
			
			try {
				Thread.sleep(sleepTime);
				waitTime += sleepTime;
			} catch (InterruptedException e) {
			}
		}
		
		return _connected;
		*/
		
		try {
			boolean countDownDone = _connectLatch.await(timeout, TimeUnit.MILLISECONDS);
			if(!countDownDone) {
				_connectLatch.countDown();
			}
		} catch (InterruptedException e) {
			//do nothing
		}
		
		return _connected.get();
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
							//break;
						}
					}
					
					if(finishConnect) {
						break;
					}
				} catch(ClosedSelectorException e) {
					logError(e);
					disconnect();
					break;
				} catch(Throwable e) {
					disconnect();
					logError(e);
					break;
				} finally {
					try {
						Thread.sleep(SLEEP_PERIOD);
					} catch(InterruptedException e) {
						logInfo("ConnectThread InterruptedException -----");
						disconnect();
						break;
					}
				}
			}
		}
	}

	protected void finishConnect() {
		try {
			boolean connected = false;
			try {
				connected = _socketChannel.finishConnect();
			} catch (NoConnectionPendingException e) {
				connected = isRealConnected();
			}
			
			if(connected) {
				//logInfo("AsyncTcpClient connected");
				
				//create io selector ----------------------
				_readSelector = Selector.open();
				_writeSelector = Selector.open();

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
				

				_readEventThread = new TaskLoopThread<TcpReadEvent>();
				_readEventThread.start();
				
				_writeEventThread = new TcpWriteEventThread(_writeSelector);
				_writeEventThread.start();

				_ioThreadPool.execute(new ReadThread());
				

				//event handler -------------------------
				//_eventHandler = _eventHandlerFactory.createHandler(_sessionId);
				//_workSelectionKey.attach(_eventHandler);
				
				//_connected = true;
				_connected.set(true);
				if(_eventHandler != null) {
					_eventHandler.didConnect(_replyMsgHandler, _socketChannel.socket().getRemoteSocketAddress());
				}
			} else {
				//_connected = false;
				_connected.compareAndSet(true, false);
			}
		} catch(Throwable e) {
			logError(e);
			disconnect();
		} finally {
			_connectLatch.countDown();
		}
	}
	
	protected ITcpReplyMessageHandler _replyMsgHandler = new ITcpReplyMessageHandler() {
		
		@Override
		public void sendMessage(IByteBuff msg) {
			_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, msg));
		}

		@Override
		public IByteBuff createBuffer() {
			return _bufferPool.borrowObject();
		}

		@Override
		public void sendMessage(MessageList<? extends IByteBuff> msgs) {
			_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, msgs));
		}
		
		@Override
		public void sendMessage(FileChannel fileChannel, long position, long byteLen) {
			_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, fileChannel, position, byteLen));
		}
		
	};
	
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
//					try {
//						Thread.sleep(SLEEP_PERIOD);
//					} catch(InterruptedException e) {
//						logInfo("IOThread InterruptedException -----");
//						break;
//					}
				}
			}
		}

		protected void handleRead(SelectionKey key) {
			SocketChannel socketChannel = (SocketChannel) key.channel();
			try {
				if(!isRealConnected()) {
					//logInfo("handleRead() not connected");
					//clearSelectionKey(key);
					disconnect();
					//return false;
					return;
				} else {
					int readLen;
					
					PooledByteBuffer buffer = _bufferPool.borrowObject();
					
					buffer.getByteBuffer().clear();
					readLen = socketChannel.read(buffer.getByteBuffer());
					
					if(readLen > 0) {
						if(_eventHandler != null) {
							_readEventThread.addTask(new TcpReadEvent(_sessionId, _eventHandler, _replyMsgHandler, buffer));
						} else {
							buffer.destroy();
						}
					} else if(readLen < 0) {
						//mostly it is -1, and means server has disconnected
						//clearSelectionKey(key);
						//logInfo("handleRead() remote peer closed");
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

	public void send(IByteBuff msg) {
		_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, msg));
	}
	
	public void send(MessageList<? extends IByteBuff> msgs) {
		_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, msgs));
	}

	public void send(FileChannel fileChannel, long position, long byteLen) {
		_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, fileChannel, position, byteLen));
	}
	
	public void send(File file) {
		_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, file));
	}
	
	public void send(ByteBuffer byteBuffer) {
		_writeEventThread.addTask(new TcpWriteEvent(_sessionId, _writeKey, byteBuffer));
	}
	
	public void send(TcpWriteEvent writeEvent) {
		_writeEventThread.addTask(writeEvent);
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
			_readEventThread.shutdown();
		} catch(Throwable e) {
			logError(e);
		}
		try {
			_writeEventThread.shutdown();
		} catch(Throwable e) {
			logError(e);
		}
		/*
		try {
			_bufferPool.close();
		} catch(Throwable e) {
			logError(e);
		}
		*/
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
		
		if(_eventHandler != null) {
			try {
				_eventHandler.didDisconnect();
			} catch(Throwable e) {
				logError(e);
			}
		}
	}

	@Override
	public boolean isConnected() {
		return isRealConnected()
				&& _connected.get()
				;
	}
	
	/**
	 * 
	 * @param maxWaitMS max wait time in milliseconds
	 * @return true:all sending tasks are completed    false:otherwise
	 * @throws InterruptedException 
	 */
	public boolean waitForSending(int maxWaitMS) throws InterruptedException {
		long didSleepTime = 0;
		long sleepInterval = Math.min(10, maxWaitMS);
		
		while(didSleepTime <= maxWaitMS) {
			if(_writeEventThread.getState() != State.RUNNABLE) {
				return true;
			}
			
			Thread.sleep(sleepInterval);
			didSleepTime += sleepInterval;
		}
		
		return false;
	}
	
	protected boolean isRealConnected() {
		return _socketChannel != null
				&& _socketChannel.socket() != null 
				&& _socketChannel.socket().isBound() 
				&& !_socketChannel.socket().isClosed()
				&& _socketChannel.socket().isConnected() 
				&& !_socketChannel.socket().isInputShutdown()
				&& !_socketChannel.socket().isOutputShutdown()
				;
	}

	protected static void logInfo(String msg) {
		//System.out.println(msg);
		logger.info(msg);
	}
	
	protected static void logError(Throwable e) {
		//e.printStackTrace();
		logger.error(null, e);
	}

	/*
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
			}
		}
	}
	*/
	
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
