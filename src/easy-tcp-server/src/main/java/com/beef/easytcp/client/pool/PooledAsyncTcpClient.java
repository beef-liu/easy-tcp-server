package com.beef.easytcp.client.pool;

import com.beef.easytcp.base.IPool;
import com.beef.easytcp.base.IPooledObject;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.client.AsyncTcpClient;
import com.beef.easytcp.client.TcpClientConfig;

public class PooledAsyncTcpClient extends AsyncTcpClient implements IPooledObject {

	protected IPool<PooledAsyncTcpClient> _backPool = null;

	public PooledAsyncTcpClient(TcpClientConfig tcpConfig, ByteBufferPool byteBufferPool) {
		super(tcpConfig, byteBufferPool);
	}

	@Override
	public void setPoolReference(IPool<? extends IPooledObject> pool) {
		if(_backPool != pool) {
			_backPool = null;
			_backPool = (IPool<PooledAsyncTcpClient>) pool;
		}
	}

	@Override
	public void returnToPool() throws Exception {
		_backPool.returnObject(this);
	}
	
}
