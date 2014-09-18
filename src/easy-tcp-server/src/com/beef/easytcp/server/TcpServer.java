package com.beef.easytcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.server.base.ChannelByteBuffer;
import com.beef.easytcp.server.base.ChannelByteBufferPoolFactory;
import com.beef.easytcp.server.config.TcpServerConfig;
import com.beef.easytcp.server.worker.AbstractWorkerDispatcher;

/**
 * The work flow (Suppose that there is 4 CPU core):
 * listener thread   * 1: do accept()
 * IO thread         * 4: do channel.read() and channel.write(). Read request bytes into ChannelByteBuffer.getReadBuffer(), and set into SelectionKey.attachment().
 * dispatcher thread * 1: dispatch request(SelectionKey) to worker threads
 * worker thread     * N: consume the request data, and write response bytes into ChannelByteBuffer.getWriteBuffer().
 * 
 * ---------------------------------------------------------------------
 * In this work flow of threads, there are features below: 
 * 1. Listener, IO, dispatcher threads are never blocked.
 * 2. Number of worker threads is depend on what kind of work is. 
 * 	For example, if each worker will operate DB and max active connection of DB pool is 256, then N = 256 is a reasonable number.    
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
	protected Selector[] _writeSelectors = null;
	
	protected GenericObjectPool<ChannelByteBuffer> _channelByteBufferPool;
	
	protected AbstractWorkerDispatcher _workerDispatcher;
	private ExecutorService _serverThreadPool;
	private boolean _isAllocateDirect = false;
	
	private AtomicInteger _clientSelectorCount = new AtomicInteger(0);
	
	
	public TcpServer(
			TcpServerConfig tcpServerConfig, 
			//boolean isAllocateDirect,
			AbstractWorkerDispatcher workerDispatcher
			) {
		_tcpServerConfig = tcpServerConfig;
		
		//if use ByteBuffer.allocateDirect(), then there is no backing array which means ByteBuffer.array() is null. And it is not convenient.
		//_isAllocateDirect = isAllocateDirect;
		
		_workerDispatcher = workerDispatcher;
	}
	
	@Override
	public void start() {
		try {
			startTcpServer();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}
	}

	@Override
	public void shutdown() {
		try {
			_workerDispatcher.destroy();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
		}

		try {
			_serverThreadPool.shutdownNow();
		} catch(Throwable e) {
			logger.error("shutdown()", e);
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

		for(int i = 0; i < _writeSelectors.length; i++) {
			try {
				closeSelector(_writeSelectors[i]);
			} catch(Throwable e) {
				logger.error("shutdown()", e);
			}
		}
		
		logger.info("Tcp Server shutted down <<<<<<<<<<<<<<<<<<<<<<<<<<<<");
	}
	
	private void startTcpServer() throws IOException {
		//The brace is just for reading clearly
		{
			//init bytebuffer pool -----------------------------
			ChannelByteBufferPoolFactory byteBufferPoolFactory = new ChannelByteBufferPoolFactory(
					_isAllocateDirect, 
					_tcpServerConfig.getSocketReceiveBufferSize(), 
					_tcpServerConfig.getSocketSendBufferSize()
					);
			GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
			byteBufferPoolConfig.setMaxIdle(_tcpServerConfig.getConnectMaxCount());
			/* old version
			byteBufferPoolConfig.setMaxActive(_PoolMaxActive);
			byteBufferPoolConfig.setMaxWait(_PoolMaxWait);
			*/
			byteBufferPoolConfig.setMaxTotal(_tcpServerConfig.getConnectMaxCount());
			byteBufferPoolConfig.setMaxWaitMillis(10);
			
			//byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
			//byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);

			_channelByteBufferPool = new GenericObjectPool<ChannelByteBuffer>(
					byteBufferPoolFactory, byteBufferPoolConfig);
		}
		
		{
			//init socket -----------------------------------------------------------------
			_serverSocketChannel = ServerSocketChannel.open();
			_serverSocketChannel.configureBlocking(false);
			
			_serverSocketChannel.socket().setReceiveBufferSize(_tcpServerConfig.getSocketReceiveBufferSize());
			//SO_TIMEOUT functional in nonblocking mode? 
			_serverSocketChannel.socket().setSoTimeout(_tcpServerConfig.getConnectTimeout());
			_serverSocketChannel.socket().bind(
					new InetSocketAddress(_tcpServerConfig.getHost(), _tcpServerConfig.getPort()), 
					_tcpServerConfig.getConnectWaitCount());
			
			//create selector
			_serverSelector = Selector.open();
			_serverSocketChannel.register(_serverSelector, SelectionKey.OP_ACCEPT);

			//init threads(listener, IO, worker dispatcher)
			int threadCount = 1 + _tcpServerConfig.getSocketIOThreadCount() + 1;
			_serverThreadPool = Executors.newFixedThreadPool(threadCount);

			long threadPeriod = 1;
			long initialDelay = 1000;
			
			//Listener
//			_serverThreadPool.scheduleAtFixedRate(
//					new ListenerThread(), initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
			_serverThreadPool.execute(new ListenerThread());
			
			//IO Threads
			int ioSelectorCount = (int) Math.ceil(_tcpServerConfig.getSocketIOThreadCount() / 2.0); 

			_readSelectors = new Selector[ioSelectorCount]; 
			for(int i = 0; i < _readSelectors.length; i++) {
				_readSelectors[i] = Selector.open();
				_serverThreadPool.execute(new ReadThread(i));
			}
			
			_writeSelectors = new Selector[ioSelectorCount]; 
			for(int i = 0; i < _writeSelectors.length; i++) {
				_writeSelectors[i] = Selector.open();
				_serverThreadPool.execute(new WriteThread(i));
			}
			
			
			//worker dispatcher
//			_serverThreadPool.scheduleAtFixedRate(
//					_workerDispatcher, initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
			_serverThreadPool.execute(_workerDispatcher);
		}
		
		logger.info("Tcp Server Started. Listen at:" 
				+ _tcpServerConfig.getHost() + ":" + _tcpServerConfig.getPort()
				+ " >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
	}
	
	protected class ListenerThread extends Thread {
		
		@Override
		public void run() {
			while(true) {
				try {
					if(_serverSelector.select() != 0) {
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
					try {
						Thread.sleep(SLEEP_PERIOD);
					} catch(InterruptedException e) {
						logger.info("ListenerThread InterruptedException -----");
						break;
					}
				}
			}
		}
	}
	
	protected enum AcceptResult {
		Accepted,
		NotAcceptedForNoClientConnect,
		NotAcceptedForReachingMaxConnection, 
		NotAcceptedForError};
	/**
	 * 
	 * @param key
	 * @return true:acceppted false:not accepted for reaching max connection count
	 */
	protected AcceptResult handleAccept(SelectionKey key) {
		if(_channelByteBufferPool.getBorrowedCount() >= _channelByteBufferPool.getMaxTotal()) {
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
			ChannelByteBuffer buffer = null;
			try {
				buffer = _channelByteBufferPool.borrowObject();
				
				//logger.debug("accepted client:".concat(socketChannel.socket().getRemoteSocketAddress().toString()));
				
				socketChannel.configureBlocking(false);
				socketChannel.socket().setSendBufferSize(_tcpServerConfig.getSocketSendBufferSize());
				socketChannel.socket().setReceiveBufferSize(_tcpServerConfig.getSocketReceiveBufferSize());
				
				int clientSelectorIndex = Math.abs(
						_clientSelectorCount.getAndIncrement() % _readSelectors.length);
				
				//logger.debug("registerring client socketChannel");
				_readSelectors[clientSelectorIndex].wakeup();
				SelectionKey selectionKey = socketChannel.register(
						_readSelectors[clientSelectorIndex], 
						SelectionKey.OP_READ 
						//| SelectionKey.OP_WRITE
						, 
						buffer);

				_writeSelectors[clientSelectorIndex].wakeup();
				SelectionKey selectionKeyW = socketChannel.register(
						_writeSelectors[clientSelectorIndex], 
						SelectionKey.OP_WRITE, 
						buffer);
				
				logger.info("accepted client:"
						.concat(socketChannel.socket().getRemoteSocketAddress().toString())
						.concat(" is registered to clientSelectors[")
						.concat(String.valueOf(clientSelectorIndex))
						.concat("]")
						);
				
				//notify event
				didConnect(selectionKey);
				
				return AcceptResult.Accepted;
			} catch(NoSuchElementException e) {
				logger.error("accept() failed. _channelByteBufferPool exhausted on reaching max connection count.", e);
				return AcceptResult.NotAcceptedForReachingMaxConnection;
			} catch (Throwable e) {
				logger.error("accept() failed.", e);
				try {
					_channelByteBufferPool.returnObject(buffer);
				} catch(Throwable e1) {
					logger.error(null, e1);
				}
				//close accepted socket channel
				closeSocketChannel(socketChannel);
				logger.info("handleAccept() Close socketChannel for Error");
				
				try {
					key.cancel();
				} catch(Throwable e1) {
					logger.error(null, e1);
				}
				
				return AcceptResult.NotAcceptedForError;
			}
		} else {
			return AcceptResult.NotAcceptedForNoClientConnect;
		}
	}
	
	protected class ReadThread extends Thread {
		private int _selectorIndex;
		
		public ReadThread(int selectorIndex) {
			_selectorIndex = selectorIndex;
		}
		
		@Override
		public void run() {
			while(true) {
				try {
					logger.debug("ReadThread[" + _selectorIndex + "] >>>>>>>>>>>>>>");
					if(_readSelectors[_selectorIndex].select() != 0) {
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

					logger.debug("ReadThread[" + _selectorIndex + "] <<<<<<<<<<<<<");
				} catch(Throwable e) {
					logger.error("IOThread error", e);
				} finally {
					/* Read Key.select() will block until data arrive, so no need to sleep
					try {
						Thread.sleep(SLEEP_PERIOD);
					} catch(InterruptedException e) {
						logger.info("IOThread InterruptedException -----");
						break;
					}
					*/
				}
			}
		}
	}

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
					/*
					try {
						Thread.sleep(SLEEP_PERIOD);
					} catch(InterruptedException e) {
						logger.info("IOThread InterruptedException -----");
						break;
					}
					*/
				}
			}
		}
	}
	
	protected boolean handleRead(SelectionKey key) {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		try {
			ChannelByteBuffer buffer = (ChannelByteBuffer)key.attachment();
			
			if(!isConnected(socketChannel.socket())) {
				clearSelectionKey(key);
				return false;
			} else {
				//if(buffer.tryLockReadBuffer()) 
				{
					long readTotalLen = 0;
					
					try {
						int readCount = 0;
						int readLen;
						while((readLen = socketChannel.read(buffer.getReadBuffer())) > 0 
								&& (readCount++) < 10) {
							readTotalLen += readLen;
						}
						
						if(readLen == -1) {
							//client is disconnected
							clearSelectionKey(key);
							//logger.debug("readLen is -1");
							return false;
						}
					} finally {
						buffer.unlockReadBufferLock();
					}
					
					if(readTotalLen > 0) {
						logger.debug("handleRead() readTotalLen:" + readTotalLen);
						_workerDispatcher.addDidReadRequest(key);
						return true;
					}
				}
				
				return false;
			}
		} catch(IOException e) {
			clearSelectionKey(key);
			return false;
		} catch(Exception e) {
			logger.error("handleRead()", e);
			return false;
		}
	}

	protected boolean handleWrite(SelectionKey key) {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		try {
			ChannelByteBuffer buffer = (ChannelByteBuffer)key.attachment();

			if(!isConnected(socketChannel.socket())) {
				clearSelectionKey(key);
				return false;
			} else {
				if(buffer.tryLockWriteBuffer()) 
				{
					try {
						long writeTotalLen = 0;
						int writeCount = 0;
						while(buffer.getWriteBuffer().remaining() > 0 
								&& (writeCount++) < 10) {
							writeTotalLen += socketChannel.write(buffer.getWriteBuffer());
						}
						
						if(writeTotalLen > 0) {
							buffer.getWriteBuffer().clear().flip();
						}
						
						return true;
					} finally {
						buffer.unlockWriteBufferLock();
					}
				}
				return false;
			}
		} catch(IOException e) {
			clearSelectionKey(key);
			return false;
		} catch(Exception e) {
			logger.error("handleWrite()", e);
			return false;
		}
	}
	
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
		try {
			
			if(selectionKey != null) {
				try {
					SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
					closeSocketChannel(socketChannel);
					
					if(selectionKey.attachment() != null) {
						//return byte buffer to pool
						_channelByteBufferPool.returnObject(
								(ChannelByteBuffer) selectionKey.attachment());
						selectionKey.attach(null);

						//notify event
						didDisconnect(selectionKey);
						
						logger.info("close socket:".concat(socketChannel.socket().getRemoteSocketAddress().toString()));
					} else {
						logger.info("close server socketChannel");
					}
				} finally {
					selectionKey.cancel();
				}
			}
		} catch(Exception e) {
			logger.error("clearSelectionKey()", e);
		}
	}
	
	protected void didConnect(SelectionKey selectionKey) {
	}

	protected void didDisconnect(SelectionKey selectionKey) {
	}
	
	
	protected static void closeSocketChannel(SocketChannel socketChannel) {
		try {
			if(!socketChannel.socket().isInputShutdown()) {
				socketChannel.socket().shutdownInput();
			}
		} catch(Exception e) {
			logger.info(e);
		}
		
		try {
			if(!socketChannel.socket().isOutputShutdown()) {
				socketChannel.socket().shutdownOutput();
			}
		} catch(Exception e) {
			//mostly client disconnected
			//logger.error(null, e);
		}
		
		try {
			if(!socketChannel.socket().isClosed()) {
				socketChannel.socket().close();
			}
		} catch(Exception e) {
			logger.error(null, e);
		}

		try {
			socketChannel.close();
		} catch(Exception e) {
			logger.error(null, e);
		}
	}
	
}
