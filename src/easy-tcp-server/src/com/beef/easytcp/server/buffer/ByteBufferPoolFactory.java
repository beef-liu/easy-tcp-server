package com.beef.easytcp.server.buffer;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

public class ByteBufferPoolFactory implements PooledObjectFactory<PooledByteBuffer> {
	private boolean _isAllocateDirect;
	private int _bufferByteSize;
	
	public ByteBufferPoolFactory(boolean isAllocateDirect, int bufferByteSize) {
		_isAllocateDirect = isAllocateDirect;
		_bufferByteSize = bufferByteSize;
	}

	@Override
	public void activateObject(PooledObject<PooledByteBuffer> obj)
			throws Exception {
		//System.out.println("activateObject");
		obj.getObject().getByteBuffer().clear();
	}

	@Override
	public void destroyObject(PooledObject<PooledByteBuffer> obj)
			throws Exception {
		//Do Nothing
		//System.out.println("destroyObject");
	}

	@Override
	public PooledObject<PooledByteBuffer> makeObject() throws Exception {
		PooledByteBuffer buffer = new PooledByteBuffer(_isAllocateDirect, _bufferByteSize);
		return new DefaultPooledObject<PooledByteBuffer>(buffer);
	}

	@Override
	public void passivateObject(PooledObject<PooledByteBuffer> obj)
			throws Exception {
		//do nothing
	}

	@Override
	public boolean validateObject(PooledObject<PooledByteBuffer> obj) {
		//do nothing
		return true;
	}

}
