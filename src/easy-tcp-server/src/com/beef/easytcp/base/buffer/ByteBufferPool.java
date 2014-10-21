package com.beef.easytcp.base.buffer;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.beef.easytcp.base.IPool;

public class ByteBufferPool implements IPool<PooledByteBuffer> {
	
	protected GenericObjectPool<PooledByteBuffer> _backPool;

	public ByteBufferPool(GenericObjectPoolConfig poolConfig,
			boolean isAllocateDirect, int bufferByteSize
			) {
		_backPool = new GenericObjectPool<PooledByteBuffer>(
				new ByteBufferPoolFactory(isAllocateDirect, bufferByteSize), 
				poolConfig);
	}

	public ByteBufferPool(GenericObjectPoolConfig poolConfig, 
			ByteBufferPoolFactory poolFactory
			) {
		_backPool = new GenericObjectPool<PooledByteBuffer>(
				poolFactory, 
				poolConfig);
	}
	
	@Override
	public void returnObject(PooledByteBuffer obj) {
		_backPool.returnObject(obj);
		//System.out.println("returnObject---------------");
	}

	@Override
	public PooledByteBuffer borrowObject() {
		try {
			final PooledByteBuffer obj = _backPool.borrowObject();
			obj.setPoolReference(this);
			
			//System.out.println("borrowObject----------");
			return obj;
		} catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	public void close() {
		_backPool.close();
	}

}
