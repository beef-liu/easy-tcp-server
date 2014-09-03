package com.beef.easytcp.base;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

public class ChannelByteBufferPoolFactory implements PooledObjectFactory<ChannelByteBuffer> {
	private boolean _isAllocateDirect;
	private int _readBufferByteSize;
	private int _writeBufferByteSize;
	
	public ChannelByteBufferPoolFactory(boolean isAllocateDirect, int readBufferByteSize, int writeBufferByteSize) {
		_isAllocateDirect = isAllocateDirect;
		_readBufferByteSize = readBufferByteSize;
		_writeBufferByteSize = writeBufferByteSize;
	}
	
	@Override
	public void activateObject(PooledObject<ChannelByteBuffer> pooledChannelByteBuffer)
			throws Exception {
		final ChannelByteBuffer channelByteBuffer = pooledChannelByteBuffer.getObject();
		channelByteBuffer.getReadBuffer().clear();
		channelByteBuffer.getWriteBuffer().clear();
	}

	@Override
	public void destroyObject(PooledObject<ChannelByteBuffer> pooledChannelByteBuffer)
			throws Exception {
		//Do nothing
	}

	@Override
	public PooledObject<ChannelByteBuffer> makeObject() throws Exception {
		final ChannelByteBuffer channelByteBuffer = new ChannelByteBuffer(
				_isAllocateDirect, _readBufferByteSize, _writeBufferByteSize);
		return new DefaultPooledObject<ChannelByteBuffer>(channelByteBuffer);
	}

	@Override
	public void passivateObject(PooledObject<ChannelByteBuffer> pooledChannelByteBuffer)
			throws Exception {
		//Do nothing
	}

	@Override
	public boolean validateObject(PooledObject<ChannelByteBuffer> pooledChannelByteBuffer) {
		//Do nothing
		return true;
	}

}
