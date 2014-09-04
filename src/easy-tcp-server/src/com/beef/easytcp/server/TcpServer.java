package com.beef.easytcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.base.ChannelByteBuffer;
import com.beef.easytcp.base.ChannelByteBufferPoolFactory;
import com.beef.easytcp.server.config.TcpServerConfig;

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
	
	protected ScheduledExecutorService _threadPool;
	protected TcpServerConfig _tcpServerConfig;

	protected ServerSocketChannel _serverSocketChannel = null;
	protected Selector _serverSelector = null;
	protected Selector[] _clientSelectors = null;
	
	protected GenericObjectPool<ChannelByteBuffer> _channelByteBufferPool;
	
	protected boolean _isAllocateDirect;
	private ScheduledExecutorService _serverThreadPool;
	
	private AtomicInteger _clientSelectorCount = new AtomicInteger(0); 
	
	public TcpServer(
			TcpServerConfig tcpServerConfig, 
			boolean isAllocateDirect) {
		_tcpServerConfig = tcpServerConfig;
		_isAllocateDirect = isAllocateDirect;
	}
	
	@Override
	public void start() {
		
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
		
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
			_serverThreadPool = Executors.newScheduledThreadPool(threadCount);

			long threadPeriod = 1;
			long initialDelay = 1000;
			
			//Listener
			_serverThreadPool.scheduleAtFixedRate(
					new ListenerThread(), initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
			
			
			//IO Threads
			_clientSelectors = new Selector[_tcpServerConfig.getSocketIOThreadCount()]; 
			for(int i = 0; i < _clientSelectors.length; i++) {
				_clientSelectors[i] = Selector.open();
				_serverThreadPool.scheduleAtFixedRate(
						new IOThread(i), initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
			}
			
			//worker dispatcher
			_serverThreadPool.scheduleAtFixedRate(
					new WorkerDispatcherThread(), initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
		}
		
	}
	
	protected class ListenerThread implements Runnable {
		@Override
		public void run() {
			try {
				if(_serverSelector.select() != 0) {
					Set<SelectionKey> keySet = _serverSelector.selectedKeys();
					
					for(SelectionKey key : keySet) {
						if(!key.isValid()) {
							continue;
						}

						//isAcceptable为true的key是监听器，不可能有read,write事件
						if(key.isAcceptable()) {
							//accept connection
							handleAccept(key);
						}
					}
					
					keySet.clear();
				}
			} catch(Throwable e) {
				logger.error("ListenerThread.run() Error Occurred", e); 
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
				
				logger.debug("accepted client:".concat(
						socketChannel.socket().getRemoteSocketAddress().toString()));
				
				socketChannel.configureBlocking(false);
				socketChannel.socket().setSendBufferSize(_tcpServerConfig.getSocketSendBufferSize());
				
				int clientSelectorIndex = Math.abs(
						_clientSelectorCount.getAndIncrement() % _clientSelectors.length);
				
				_clientSelectors[clientSelectorIndex].wakeup();
				SelectionKey selectionKey = socketChannel.register(
						_clientSelectors[clientSelectorIndex], 
						SelectionKey.OP_READ | SelectionKey.OP_WRITE, 
						buffer);
				
				logger.info("accept() succeeded. client socketChannel is registered to clientSelectors["
						.concat(String.valueOf(clientSelectorIndex)).concat("]"));
				
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
	
	protected class IOThread implements Runnable {
		private int _selectorIndex;
		
		public IOThread(int selectorIndex) {
			_selectorIndex = selectorIndex;
		}
		
		@Override
		public void run() {
		}
	}
	
	protected class WorkerDispatcherThread implements Runnable {

		@Override
		public void run() {
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
