package com.beef.easytcp.client.pool;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.beef.easytcp.base.IPool;
import com.beef.easytcp.client.TcpClientConfig;

public class SyncTcpClientPool implements IPool<PooledSyncTcpClient> {
	protected GenericObjectPool<PooledSyncTcpClient> _backPool = null;

	public SyncTcpClientPool(GenericObjectPoolConfig poolConfig, TcpClientConfig tcpConfig) {
		_backPool = new GenericObjectPool<PooledSyncTcpClient> (
				new SyncTcpClientPoolFactory(tcpConfig), 
				poolConfig);
	}
	
	@Override
	public void returnObject(PooledSyncTcpClient obj) {
		_backPool.returnObject(obj);
	}

	@Override
	public PooledSyncTcpClient borrowObject() {
		try {
			final PooledSyncTcpClient obj = _backPool.borrowObject();
			obj.setPoolReference(this);
			
			return obj;
		} catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void close() {
		_backPool.close();
	}

}
