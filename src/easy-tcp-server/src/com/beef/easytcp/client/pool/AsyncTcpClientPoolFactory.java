package com.beef.easytcp.client.pool;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import com.beef.easytcp.client.TcpClientConfig;

public class AsyncTcpClientPoolFactory implements PooledObjectFactory<PooledAsyncTcpClient> {
	private TcpClientConfig _tcpConfig;
	private int _byteBufferPoolSize;
	
	public AsyncTcpClientPoolFactory(TcpClientConfig tcpConfig, int byteBufferPoolSize) {
		_tcpConfig = tcpConfig;
		_byteBufferPoolSize = byteBufferPoolSize;
	}
	
	@Override
	public void activateObject(PooledObject<PooledAsyncTcpClient> obj)
			throws Exception {
	}

	@Override
	public void destroyObject(PooledObject<PooledAsyncTcpClient> obj)
			throws Exception {
		obj.getObject().disconnect();
	}

	@Override
	public PooledObject<PooledAsyncTcpClient> makeObject() throws Exception {
		final PooledAsyncTcpClient tcpClient = new PooledAsyncTcpClient(_tcpConfig, _byteBufferPoolSize);
		return new DefaultPooledObject<PooledAsyncTcpClient>(tcpClient);
	}

	@Override
	public void passivateObject(PooledObject<PooledAsyncTcpClient> obj)
			throws Exception {
		//obj.getObject().getSocket().shutdownInput();
	}

	@Override
	public boolean validateObject(PooledObject<PooledAsyncTcpClient> obj) {
		//do nothing
		return obj.getObject().isConnected();
	}

}
