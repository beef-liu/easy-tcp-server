package com.beef.easytcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.buffer.PooledByteBuffer;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.base.handler.TcpReadEvent;
import com.beef.easytcp.base.handler.TcpSession;
import com.beef.easytcp.base.handler.TcpWriteEvent;
import com.beef.easytcp.base.handler.TcpWriteEventThread;
import com.beef.easytcp.base.thread.ITaskLoopThreadFactory;
import com.beef.easytcp.base.thread.TaskLoopThread;
import com.beef.easytcp.base.thread.pool.LoopTaskThreadFixedPool;

/**
 * The work flow:
 * listener thread   * 1: do accept()
 * Read thread       * r: do channel.read() to read request as a readEvent add to ReadEventThreadPool.
 * ReadEventThread   * n: ReadEventThread are pooled. The pool reuses cached threads to handle request data, and handle event sequentially for one connection 
 * WriteEventThread   * m: WriteEventThread are pooled. The pool reuses cached threads to handle request data, and handle event sequentially for one connection 
 * 
 * ---------------------------------------------------------------------
 * In this work flow of threads, there are some features below:
 * 1. Suppose that there is 4 CPU core, then setting r = 1 or 2 is reasonable, I think. Because it is just dispatch received messages to ReadEventThreadPool.  
 * 2. Easy to support massive connections, for example more than 10000. Because there is small number of threads, not the case that 10000 threads for 10000 connection.
 * 3. About number of WriteEventThread, setting m = 1 or 2  is reasonable, I think. Because it is just do channel.write() with replies. 
 * 4. About number of ReadEventThread, a good choice is depend on what kind of business is. Because every thread will invoke handler's method which maybe take a while.
 *    And actually this number only can be power of 2, even you assigned a number which is not a power of 2, but pool size will be power(2, (int)(log(N) / log(2)))
 *    pool size made to power of 2 is for faster to choose thread. 
 *    ---------------------
 *    For example, if there is many IO operations in handling request, then you need more threads(16?, 32?, 64?). 
 * 	  For example, if each handling will operate DB and average time cost 10 ms, and you hope to support 10000 connection, then N = 128 is a reasonable number. 
 *    And too big number of ReadEventThread maybe is not a good choice, because it maybe increase cost on CPU switching between many threads.  
 * 
 * ---------------------------------------------------------------------
 * 
 * @author XingGu Liu
 *
 */
public class TcpServer implements IServer {
	private final static Logger logger = Logger.getLogger(TcpServer.class);
	
	protected final static long SLEEP_PERIOD = 1;
	
	protected TcpServerConfig _tcpServerConfig;

	protected ServerSocketChannel _serverSocketChannel = null;
	protected Selector _serverSelector = null;
	protected Selector[] _readSelectors = null;
	protected ReadThread[] _readThreads = null;
	//protected Selector[] _writeSelectors = null;
	//protected Selector _writeSelector = null;
	protected LoopTaskThreadFixedPool<TcpWriteEvent> _writeEventThreadPool = null;
	
	protected boolean _isBufferPoolAssigned = false;
	protected ByteBufferPool _bufferPool;
	protected ITcpEventHandlerFactory _eventHandlerFactory;
	
	protected ExecutorService _serverThreadPool;
	protected ListenerThread _listenerThread;
	protected LoopTaskThreadFixedPool<TcpReadEvent> _readEventThreadPool;
	
	
	
	protected boolean _isAllocateDirect = false;
	//protected boolean _isSyncInvokeDidReceivedMsg = true;
	
	private AtomicInteger _clientSelectorCount = new AtomicInteger(0);
	protected AtomicInteger _sessionIdSeed = new AtomicInteger(0);
	protected AtomicInteger _connecttingSocketCount = new AtomicInteger(0);
	
	/**
	 * 
	 * @param tcpServerConfig
	 * @param isAllocateDirect
	 * @param eventHandlerFactory
	 * @param isSyncInvokeDidReceivedMsg if true, then TcpServer will invoke didReceivedMsg() synchronized, otherwise will invoke in a thread.
	 */
	public TcpServer(
			TcpServerConfig tcpServerConfig, 
			boolean isAllocateDirect,
			ITcpEventHandlerFactory eventHandlerFactory
			//boolean isSyncInvokeDidReceivedMsg
			) {
		_tcpServerConfig = tcpServerConfig;
		
		//if use ByteBuffer.allocateDirect(), then there is no backing array which means ByteBuffer.array() is null.
		_isAllocateDirect = isAllocateDirect;
		
		_eventHandlerFactory = eventHandlerFactory;
		//_isSyncInvokeDidReceivedMsg = isSyncInvokeDidReceivedMsg;
	}

	public TcpServer(
			TcpServerConfig tcpServerConfig, 
			boolean isAllocateDirect,
			ITcpEventHandlerFactory eventHandlerFactory,
			ByteBufferPool bufferPool
			) {
		this(tcpServerConfig, isAllocateDirect, eventHandlerFactory);
		
		_bufferPool = bufferPool;
		_isBufferPoolAssigned = true;
	}
	
	
	
	@Override
	public void start() {
		try {
			startTcpServer();
		} catch(Throwable e) {
			logger.error("start()", e);
		}
	}

	@Override
	public void shutdown() {
		try {
			_readEventThreadPool.shutdown();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}
		
		try {
			_writeEventThreadPool.shutdown();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}
		
		try {
			_listenerThread.stopThread();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}
		
		try {
			_serverThreadPool.shutdownNow();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}

		try {
			for(int i = 0; i < _readThreads.length; i++) {
				try {
					_readThreads[i].stopThread();
				} catch(Throwable e) {
					logger.error("shutdown()", e);
				}
			}
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}

		if(!_isBufferPoolAssigned) {
			try {
				_bufferPool.close();
			} catch(Throwable e) {
				logger.error("shutdown()", e);
			}
		}
		
		try {
			closeSelector(_serverSelector);
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}
		
		for(int i = 0; i < _readSelectors.length; i++) {
			try {
				closeSelector(_readSelectors[i]);
			} catch(Throwable e) {
				logger.error("shutdown()", e);
			}
		}
		
		/*
		for(int i = 0; i < _writeSelectors.length; i++) {
			try {
				closeSelector(_writeSelectors[i]);
			} catch(Throwable e) {
				logger.error("shutdown()", e);
			}
		}
		*/
		/*
		try {
			closeSelector(_writeSelector);
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}
		*/
		Iterator<TaskLoopThread<TcpWriteEvent>> iterWriteEventThread = _writeEventThreadPool.getAllThreads();
		while(iterWriteEventThread.hasNext()) {
			try {
				closeSelector(((TcpWriteEventThread) iterWriteEventThread.next()).getWriteSelector());
			} catch(Throwable e) {
				logger.error("shutdown()", e);
			}
		}
		
		logger.info("Tcp Server shutted down <<<<<<<<<<<<<<<<<<<<<<<<<<<<");
	}
	
	public int getCurrentConnectionCount() {
		return _connecttingSocketCount.get();
	}
	
	private void startTcpServer() throws IOException {
		if(!_isBufferPoolAssigned)
		{
			//init bytebuffer pool -----------------------------
			int bufferByteSize = _tcpServerConfig.getSocketReceiveBufferSize();
//			ByteBufferPoolFactory byteBufferPoolFactory = new ByteBufferPoolFactory(
//					_isAllocateDirect, bufferByteSize);
			
			GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
			byteBufferPoolConfig.setMaxIdle(_tcpServerConfig.getConnectMaxCount());
			/* old version
			byteBufferPoolConfig.setMaxActive(_PoolMaxActive);
			byteBufferPoolConfig.setMaxWait(_PoolMaxWait);
			*/
			byteBufferPoolConfig.setMaxTotal(_tcpServerConfig.getConnectMaxCount() * 2);
			byteBufferPoolConfig.setMaxWaitMillis(1000);
			
			//byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
			//byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);

			_bufferPool = new ByteBufferPool(
					byteBufferPoolConfig, _isAllocateDirect, bufferByteSize); 
		}
		
		{
			//init socket -----------------------------------------------------------------
			_serverSocketChannel = ServerSocketChannel.open();

			_serverSocketChannel.socket().setSoTimeout(_tcpServerConfig.getSoTimeout());

			_serverSocketChannel.configureBlocking(false);
			
			_serverSocketChannel.socket().setReceiveBufferSize(_tcpServerConfig.getSocketReceiveBufferSize());
			//SO_TIMEOUT functional in nonblocking mode? 
			_serverSocketChannel.socket().bind(
					new InetSocketAddress(_tcpServerConfig.getHost(), _tcpServerConfig.getPort()), 
					_tcpServerConfig.getConnectWaitCount());
			
			//create selector
			_serverSelector = Selector.open();
			_serverSocketChannel.register(_serverSelector, SelectionKey.OP_ACCEPT);

			//init threads(listener, IO, worker dispatcher)
			{
				int threadCount = 1 + _tcpServerConfig.getSocketIOThreadCount() + 1;
				_serverThreadPool = Executors.newCachedThreadPool();
			}
			{
				int threadCount = _tcpServerConfig.getReadEventThreadCount();
				_readEventThreadPool = new LoopTaskThreadFixedPool<TcpReadEvent>(
						threadCount);
			}

			//Listener
//			long threadPeriod = 1;
//			long initialDelay = 1000;
//			_serverThreadPool.scheduleAtFixedRate(
//					new ListenerThread(), initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
			_listenerThread = new ListenerThread();
			_serverThreadPool.execute(_listenerThread);
			
			//IO Threads
			//int ioSelectorCount = (int) Math.ceil(_tcpServerConfig.getSocketIOThreadCount() / 2.0);
			int ioSelectorCount = _tcpServerConfig.getSocketIOThreadCount();

			_readSelectors = new Selector[ioSelectorCount];
			_readThreads = new ReadThread[ioSelectorCount];
			for(int i = 0; i < _readSelectors.length; i++) {
				_readSelectors[i] = Selector.open();
				_readThreads[i] = new ReadThread(i);
				_serverThreadPool.execute(_readThreads[i]);
			}
			
			/*
			_writeSelectors = new Selector[ioSelectorCount]; 
			for(int i = 0; i < _writeSelectors.length; i++) {
				_writeSelectors[i] = Selector.open();
				_serverThreadPool.execute(new WriteThread(i));
			}
			*/
			_writeEventThreadPool = new LoopTaskThreadFixedPool<TcpWriteEvent>(
					_tcpServerConfig.getWriteEventThreadCount(), new ITaskLoopThreadFactory<TcpWriteEvent>() {

						@Override
						public TaskLoopThread<TcpWriteEvent> createThread() {
							Selector writeSelector;
							try {
								writeSelector = Selector.open();
							} catch (IOException e) {
								logger.error(null, e);
								throw new RuntimeException(e);
							}
							
							return (TaskLoopThread<TcpWriteEvent>) (new TcpWriteEventThread(writeSelector));
						}
					});
			
			//_writeSelector = Selector.open();
			
			
			//worker dispatcher
			//_serverThreadPool.execute(_workerDispatcher);
		}
		
		logger.info("Tcp Server Started. Listen at:" 
				+ _tcpServerConfig.getHost() + ":" + _tcpServerConfig.getPort()
				+ " >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
	}
	
	protected class ListenerThread extends Thread {
		private volatile boolean _stopFlg = false;
		
		public void stopThread() {
			_stopFlg = true;
		}
		
		@Override
		public void run() {
			while(!_stopFlg) {
				try {
					//logger.debug("ListenerThread.run() ----");
					if(_serverSelector.select(1000) != 0) {
						//logger.debug("ListenerThread.run() <<<<<<<<<<<<<");
						Set<SelectionKey> keySet = _serverSelector.selectedKeys();
						
						for(SelectionKey key : keySet) {
							if(!key.isValid()) {
								continue;
							}

							if(key.isAcceptable()) {
								handleAccept(key);
							}
						}
						
						keySet.clear();
					}
					
				} catch(Throwable e) {
					logger.error("ListenerThread.run() Error Occurred", e); 
				} finally {
//					try {
//						Thread.sleep(SLEEP_PERIOD);
//					} catch(InterruptedException e) {
//						logger.info("ListenerThread InterruptedException -----");
//						break;
//					}
				}
			}
		}
	}
	
	protected enum AcceptResult {
		Accepted,
		NotAcceptedForNoClientConnect,
		NotAcceptedForReachingMaxConnection, 
		NotAcceptedForError
	};
	
	protected class ReplyMsgHandler implements ITcpReplyMessageHandler {
		private int _sessionId;
		private SelectionKey _writeKey;
		
		public ReplyMsgHandler(int sessionId, SelectionKey writeKey) {
			_sessionId = sessionId;
			_writeKey = writeKey;
		}
		
		@Override
		public void sendMessage(IByteBuff msg) {
			_writeEventThreadPool.execute(new TcpWriteEvent(_sessionId, _writeKey, msg));
		}

		@Override
		public IByteBuff createBuffer() {
			return _bufferPool.borrowObject();
		}

		@Override
		public void sendMessage(MessageList<? extends IByteBuff> msgs) {
			_writeEventThreadPool.execute(new TcpWriteEvent(_sessionId, _writeKey, msgs));
		}
		
	}
		
	/**
	 * 
	 * @param key
	 * @return true:acceppted false:not accepted for reaching max connection count
	 */
	protected AcceptResult handleAccept(SelectionKey key) {
		if(_connecttingSocketCount.get() >= _tcpServerConfig.getConnectMaxCount()) {
			logger.error("Connection achieve max, not accept more");
			return AcceptResult.NotAcceptedForReachingMaxConnection;
		}
		
		SocketChannel socketChannel = null;
		try {
			ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
			//accept
			socketChannel = serverChannel.accept();
			
		} catch(Throwable e) {
			try {
				key.cancel();
			} catch(Throwable e1) {
				logger.error(null, e1);
			}
			
			return AcceptResult.NotAcceptedForError;
		}

		//accept successfully
		if(socketChannel != null) {
			try {
				//logger.debug("accept() 000");
				//configure socket channel ----------------------------
				socketChannel.configureBlocking(false);
				socketChannel.socket().setSendBufferSize(_tcpServerConfig.getSocketSendBufferSize());
				socketChannel.socket().setReceiveBufferSize(_tcpServerConfig.getSocketReceiveBufferSize());
				
				int clientSelectorIndex = Math.abs(
						_clientSelectorCount.getAndIncrement() % _readSelectors.length);
				
				//register socket channel ------------------------------
				/*
				_writeSelectors[clientSelectorIndex].wakeup();
				SelectionKey selectionKey = socketChannel.register(
						_writeSelectors[clientSelectorIndex], 
						SelectionKey.OP_WRITE, 
						sessionObj);
				*/

				int sessionId = _sessionIdSeed.incrementAndGet();
				//logger.debug("accept() 001");
				TcpWriteEventThread writeEventThread = (TcpWriteEventThread) _writeEventThreadPool.getThreadOfGroup(sessionId);
				//logger.debug("accept() 002");
				SelectionKey writeKey = null;
				try {
					writeEventThread.suspendThread();
					writeEventThread.getWriteSelector().wakeup();
					//logger.debug("accept() 002a");
					writeKey = socketChannel.register(
							writeEventThread.getWriteSelector(), 
							SelectionKey.OP_WRITE
							);
				} finally {
					writeEventThread.resumeThread();
				}
				//logger.debug("accept() 003");
				final ITcpEventHandler eventHandler = _eventHandlerFactory.createHandler(sessionId);
				//logger.debug("accept() 004");
				TcpSession tcpSession = new TcpSession(sessionId, writeKey, eventHandler, new ReplyMsgHandler(sessionId, writeKey));
				writeKey.attach(tcpSession);

				//notify event
				try {
					int socketCnt = _connecttingSocketCount.incrementAndGet();
					//logger.debug("_connecttingSocketCount(incre):" + socketCnt);
					//logger.debug("accept() 005");
					eventHandler.didConnect(tcpSession.getReplyMsgHandler(), socketChannel.socket().getRemoteSocketAddress());
					//logger.debug("accept() 006");
				} catch(Throwable e) {
					logger.error(null, e);
				}
				
				try {
					_readThreads[clientSelectorIndex].suspendThread();
					_readSelectors[clientSelectorIndex].wakeup();
					//logger.debug("accept() 007");
					SelectionKey readKey = socketChannel.register(
							_readSelectors[clientSelectorIndex], 
							SelectionKey.OP_READ 
							//| SelectionKey.OP_WRITE
							, tcpSession
							);
				} finally {
					_readThreads[clientSelectorIndex].resumeThread();
				}
				//logger.debug("accept() 008");

//				logger.debug("accepted client:"
//						.concat(socketChannel.socket().getRemoteSocketAddress().toString())
//						.concat(" is registered to clientSelectors[")
//						.concat(String.valueOf(clientSelectorIndex))
//						.concat("]")
//						);
				
				return AcceptResult.Accepted;
			} catch (Throwable e) {
				logger.error("accept() failed.", e);

				clearSelectionKey(key);
				
				return AcceptResult.NotAcceptedForError;
			}
		} else {
			logger.error("accept() key.channel() is null");
			return AcceptResult.NotAcceptedForNoClientConnect;
		}
	}
	
	protected class ReadThread extends Thread {
		private int _selectorIndex;

		private volatile boolean _stopFlg = false;
		private Object _waitObj = new Object();
		private volatile boolean _waitFlg = false;

		public void suspendThread() {
			_waitFlg = true;
		}
		
		public void resumeThread() {
			_waitFlg = false;
			synchronized (_waitObj) {
				_waitObj.notifyAll();
			}
		}
		
		public void stopThread() {
			_stopFlg = true;
		}
		
		public ReadThread(int selectorIndex) {
			_selectorIndex = selectorIndex;
		}
		
		@Override
		public void run() {
			while(!_stopFlg) {
				try {
					if(_waitFlg) {
						synchronized (_waitObj) {
							try {
								_waitObj.wait();
							} catch (InterruptedException e1) {
								//System.out.println("wait interrupted-----");
							}
						}
					}
					
					//logger.debug("ReadThread[" + _selectorIndex + "] >>>>>>>>>>>>>>");
					if(_readSelectors[_selectorIndex].select(1000) != 0) {
						//logger.debug("ReadThread[" + _selectorIndex + "] <<<<<<<<<<<<<<");
						Set<SelectionKey> keySet = _readSelectors[_selectorIndex].selectedKeys();
						
						for(SelectionKey key : keySet) {
							try {
								if(!key.isValid()) {
									continue;
								}

								if(key.isReadable()) {
									handleRead(key);
								}
							} catch(CancelledKeyException e) {
								logger.debug("IOThread key canceled");
							} catch(Exception e) {
								logger.error("IOThread error", e);
							}
						}
						
						keySet.clear();
					}

					//logger.debug("ReadThread[" + _selectorIndex + "] <<<<<<<<<<<<<");
				} catch(Throwable e) {
					logger.error("IOThread error", e);
				} finally {
					//I don't know why Key.select() will wait forever if not sleep a while;
//					try {
//						Thread.sleep(SLEEP_PERIOD);
//					} catch(InterruptedException e) {
//						logger.info("IOThread InterruptedException -----");
//						break;
//					}
				}
			}
		}
	}
	
	protected void handleRead(SelectionKey key) {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		try {
			if(!isConnected(socketChannel.socket())) {
				clearSelectionKey(key);
				//return false;
				return;
			} else {
				final MessageList<PooledByteBuffer> messages = new MessageList<PooledByteBuffer>();

				int readLen;
				PooledByteBuffer pooledBuffer;
				
				try {
					while(true) {
						pooledBuffer = _bufferPool.borrowObject();
						
						try {
							pooledBuffer.getByteBuffer().clear();
							readLen = socketChannel.read(pooledBuffer.getByteBuffer());
						} catch(Throwable e) {
							pooledBuffer.returnToPool();
							throw e;
						}
						
						if(readLen > 0) {
							messages.add(pooledBuffer);
						} else {
							if(readLen < 0) {
								//mostly it is -1, and means client has disconnected
								clearSelectionKey(key);
								
								Iterator<PooledByteBuffer> iter = messages.iterator();
								while(iter.hasNext()) {
									(iter.next()).returnToPool();
								}
								messages.clear();
							}

							//return pooledBuffer
							pooledBuffer.returnToPool();
							break;
						}
					}
				} catch(Throwable e) {
					Iterator<PooledByteBuffer> iter = messages.iterator();
					while(iter.hasNext()) {
						(iter.next()).returnToPool();
					}
					messages.clear();
					
					throw e;
				}
				
				if(messages.size() > 0) {
					//fire event
					final TcpSession tcpSession = (TcpSession) key.attachment();
					final ITcpEventHandler eventHandler = tcpSession.getEventHandler();
					
					//if(_isSyncInvokeDidReceivedMsg) 
					{
						try {
							_readEventThreadPool.execute(new TcpReadEvent(tcpSession.getSessionId(), eventHandler, tcpSession.getReplyMsgHandler(), messages));
						} catch(Throwable e) {
							logger.error(null, e);
						} 
						/* Warning: need return to pool in implementation class
						finally {
							Iterator<PooledByteBuffer> iter = messages.iterator();
							while(iter.hasNext()) {
								(iter.next()).returnToPool();
							}
							//messages.clear();
						}
						*/
					} 
					/*
					else {
						_readEventHandlerThreadPool.execute(new Runnable() {
							
							@Override
							public void run() {
								try {
									eventHandler.didReceivedMsg(messages);
								} catch(Throwable e) {
									logger.error(null, e);
								} finally {
									Iterator<PooledByteBuffer> iter = messages.iterator();
									while(iter.hasNext()) {
										(iter.next()).returnToPool();
									}
									//messages.clear();
								}
							}
						});
					}
					*/
				}
				
				return;
			}
		} catch(IOException e) {
			//mostly "Connection reset by peer"
			logger.error("handleRead() ".concat(e.getMessage()));
			clearSelectionKey(key);
			//return false;
		} catch(Throwable e) {
			logger.error("handleRead()", e);
			//return false;
		}
	}

	/*
	protected class WriteThread extends Thread {
		private int _selectorIndex;
		
		public WriteThread(int selectorIndex) {
			_selectorIndex = selectorIndex;
		}
		
		@Override
		public void run() {
			while(true) {
				try {
					//logger.debug("WriteThread[" + _selectorIndex + "] >>>>>>>>>>>>>>");
					if(_writeSelectors[_selectorIndex].select() != 0) {
						Set<SelectionKey> keySet = _writeSelectors[_selectorIndex].selectedKeys();
						
						for(SelectionKey key : keySet) {
							try {
								if(!key.isValid()) {
									continue;
								}

								if(key.isWritable()) {
									handleWrite(key);
								}
							} catch(CancelledKeyException e) {
								logger.debug("IOThread key canceled");
							} catch(Exception e) {
								logger.error("IOThread error", e);
							}
						}
						
						keySet.clear();
					}

					//logger.debug("WriteThread[" + _selectorIndex + "] <<<<<<<<<<<<<");
				} catch(Throwable e) {
					logger.error("IOThread error", e);
				} finally {
//					try {
//						Thread.sleep(SLEEP_PERIOD);
//					} catch(InterruptedException e) {
//						logger.info("IOThread InterruptedException -----");
//						break;
//					}
				}
			}
		}
	}
	
	protected void handleWrite(SelectionKey key) {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		try {
			ChannelByteBuffer buffer = (ChannelByteBuffer)key.attachment();

			if(!isConnected(socketChannel.socket())) {
				clearSelectionKey(key);
				//return false;
				return;
			} else {
				//if(buffer.tryLockWriteBuffer())
				if(!buffer.isLockedWriteBuffer())
				{
					if(buffer.getWriteBuffer().remaining() == 0) {
						//return false;
						return;
					}
					
					socketChannel.write(buffer.getWriteBuffer());
				}
				//return false;
			}
		} catch(IOException e) {
			clearSelectionKey(key);
			//return false;
		} catch(Throwable e) {
			logger.error("handleWrite()", e);
			//return false;
		}
	}
	*/
	
    protected static boolean isConnected(Socket socket) {
		return socket != null && socket.isBound() && !socket.isClosed()
			&& socket.isConnected() && !socket.isInputShutdown()
			&& !socket.isOutputShutdown();
    }

	protected void closeSelector(Selector selector) throws IOException {
		selector.wakeup();
		Set<SelectionKey> keySet = selector.selectedKeys();
		
		for(SelectionKey selectionKey : keySet) {
			clearSelectionKey(selectionKey);			
		}
		
		selector.close();
	}
    
	protected void clearSelectionKey(SelectionKey selectionKey) {
		if(SocketChannelUtil.clearSelectionKey(selectionKey)) {
			if(selectionKey.attachment() != null) {
				final TcpSession tcpSession = (TcpSession) selectionKey.attachment();
				final ITcpEventHandler eventHandler = 
						tcpSession.getEventHandler();
				try {
					eventHandler.didDisconnect();
				} catch(Throwable e) {
					logger.error(null, e);
				} finally {
					try {
						int socketCnt = _connecttingSocketCount.decrementAndGet();
						//logger.debug("_connecttingSocketCount(decre):" + socketCnt);
					} catch(Throwable e) {
						logger.error(null, e);
					}
					/*
					try {
						if(eventHandler.getReadKey() != null) {
							SocketChannelUtil.clearSelectionKey(eventHandler.getReadKey());
						}
					} catch(Throwable e) {
						logger.error(null, e);
					}
					*/
					try {
						if(tcpSession.getWriteKey() != null) {
							SocketChannelUtil.clearSelectionKey(tcpSession.getWriteKey());
						}
					} catch(Throwable e) {
						logger.error(null, e);
					}
					
					/*
					try {
						eventHandler.destroy();
					} catch(Throwable e) {
						logger.error(null, e);
					}
					*/
				}
			}
		}
	}
	
}
