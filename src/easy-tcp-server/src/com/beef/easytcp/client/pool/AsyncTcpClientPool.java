package com.beef.easytcp.client.pool;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.beef.easytcp.base.IPool;
import com.beef.easytcp.client.TcpClientConfig;

public class AsyncTcpClientPool implements IPool<PooledAsyncTcpClient> {
	protected GenericObjectPool<PooledAsyncTcpClient> _backPool = null;

	public AsyncTcpClientPool(GenericObjectPoolConfig poolConfig, TcpClientConfig tcpConfig, int byteBufferPoolSize) {
		_backPool = new GenericObjectPool<PooledAsyncTcpClient> (
				new AsyncTcpClientPoolFactory(tcpConfig, byteBufferPoolSize), 
				poolConfig);
	}
	
	@Override
	public void returnObject(PooledAsyncTcpClient obj) {
		_backPool.returnObject(obj);
	}

	@Override
	public PooledAsyncTcpClient borrowObject() {
		try {
			final PooledAsyncTcpClient obj = _backPool.borrowObject();
			obj.setPoolReference(this);
			
			return obj;
		} catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}

}
