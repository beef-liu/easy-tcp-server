package com.beef.easytcp.base.buffer;

import com.beef.easytcp.base.IPool;
import com.beef.easytcp.base.buffer.PooledByteBuffer;

import simplepool.base.BasePoolConfig;
import simplepool.base.GenericObjPool;
import simplepool.base.abstracts.IObjFactory;

public class ByteBufferPool2 implements IPool<PooledByteBuffer> {
	private final boolean _isAllocateDirect;
	private final int _bufferByteSize;

	private final GenericObjPool<PooledByteBuffer> _backPool;
	
	public ByteBufferPool2(
			BasePoolConfig poolConfig,
			boolean isAllocateDirect, int bufferByteSize
			) {
		
		_isAllocateDirect = isAllocateDirect;
		_bufferByteSize = bufferByteSize;
		
		_backPool = new GenericObjPool<PooledByteBuffer>(
				poolConfig,
				new IObjFactory<PooledByteBuffer>() {
					
					@Override
					public PooledByteBuffer makeObject() {
						return new PooledByteBuffer(_isAllocateDirect, _bufferByteSize);
					}

					@Override
					public void destroyObject(PooledByteBuffer obj) {
						//Do Nothing
					}

					@Override
					public boolean validateObject(PooledByteBuffer obj) {
						return true;
					}

					@Override
					public void activateObject(PooledByteBuffer obj) {
						//Do Nothing
					}

					@Override
					public void passivateObject(PooledByteBuffer obj) {
						//Do Nothing
					}
				});
	}
	

	@Override
	public void returnObject(PooledByteBuffer obj) {
		_backPool.returnObject(obj);
	}

	@Override
	public PooledByteBuffer borrowObject() {
		final PooledByteBuffer obj = _backPool.borrowObject();
		if(obj == null) {
			return null;
		} else {
			obj.setPoolReference(this);
			return obj;
		}
	}

	@Override
	public void close() {
		_backPool.close();
	}

}
