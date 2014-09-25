package com.beef.easytcp.client.pool;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import com.beef.easytcp.client.TcpClientConfig;

public class SyncTcpClientPoolFactory implements PooledObjectFactory<PooledSyncTcpClient> {
	private TcpClientConfig _tcpConfig;
	
	public SyncTcpClientPoolFactory(TcpClientConfig tcpConfig) {
		_tcpConfig = tcpConfig;
	}
	
	@Override
	public void activateObject(PooledObject<PooledSyncTcpClient> obj)
			throws Exception {
	}

	@Override
	public void destroyObject(PooledObject<PooledSyncTcpClient> obj)
			throws Exception {
		obj.getObject().disconnect();
	}

	@Override
	public PooledObject<PooledSyncTcpClient> makeObject() throws Exception {
		PooledSyncTcpClient tcpClient = new PooledSyncTcpClient(_tcpConfig);
		return new DefaultPooledObject<PooledSyncTcpClient>(tcpClient);
	}

	@Override
	public void passivateObject(PooledObject<PooledSyncTcpClient> obj)
			throws Exception {
		//do nothing
	}

	@Override
	public boolean validateObject(PooledObject<PooledSyncTcpClient> obj) {
		//do nothing
		return obj.getObject().isConnected();
	}

}
