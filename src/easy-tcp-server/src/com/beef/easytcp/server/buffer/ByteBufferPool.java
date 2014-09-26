package com.beef.easytcp.server.buffer;

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
	
	@Override
	public void returnObject(PooledByteBuffer obj) {
		_backPool.returnObject(obj);
		//System.out.println("returnObject---------------");
	}

	@Override
	public PooledByteBuffer borrowObject() {
		try {
			PooledByteBuffer obj = _backPool.borrowObject();
			obj.setPoolReference(this);
			
			//System.out.println("borrowObject----------");
			return obj;
		} catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}
	

}
