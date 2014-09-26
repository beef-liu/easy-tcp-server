package com.beef.easytcp.client.pool;

import com.beef.easytcp.base.IPool;
import com.beef.easytcp.base.IPooledObject;
import com.beef.easytcp.client.SyncTcpClient;
import com.beef.easytcp.client.TcpClientConfig;

public class PooledSyncTcpClient extends SyncTcpClient implements IPooledObject {
	protected IPool<PooledSyncTcpClient> _backPool = null;

	public PooledSyncTcpClient(TcpClientConfig tcpConfig) {
		super(tcpConfig);
	}

	@Override
	public void setPoolReference(IPool<? extends IPooledObject> pool) {
		if(_backPool != pool) {
			_backPool = null;
			_backPool = (IPool<PooledSyncTcpClient>) pool;
		}
	}

	@Override
	public void returnToPool() throws Exception {
		_backPool.returnObject(this);
	}

}
