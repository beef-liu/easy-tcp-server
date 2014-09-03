package com.beef.easytcp.server;

import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

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
	protected ScheduledExecutorService _threadPool;
	protected TcpServerConfig _tcpServerConfig;

	protected ServerSocketChannel _serverSocketChannel = null;
	protected Selector _serverSelector = null;
	protected Selector[] _ioSelectors = null;
	
	protected GenericObjectPool<ChannelByteBuffer> _channelByteBufferPool;
	
	protected boolean _isAllocateDirect;
	
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
	
	private void startTcpServer() {
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
		byteBufferPoolConfig.setMaxWaitMillis(500);
		
		//byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
		//byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);

		_channelByteBufferPool = new GenericObjectPool<ChannelByteBuffer>(
				byteBufferPoolFactory, byteBufferPoolConfig);
		
	}
	

}
