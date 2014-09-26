package com.beef.easytcp.server.buffer;

import java.nio.ByteBuffer;

import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.IPool;
import com.beef.easytcp.base.IPooledObject;

public class PooledByteBuffer extends ByteBuff implements IPooledObject {
	protected IPool<PooledByteBuffer> _backPool = null;
	
	public PooledByteBuffer(boolean isAllocateDirect, int bufferByteSize) {
		super(isAllocateDirect, bufferByteSize);
	}
	
	public PooledByteBuffer(ByteBuffer byteBuff) {
		super(byteBuff);
	}
	
	@Override
	public void setPoolReference(IPool<? extends IPooledObject> pool) {
		if(_backPool != pool) {
			_backPool = null;
			_backPool = (IPool<PooledByteBuffer>) pool;
		}
	}

	@Override
	public void returnToPool() {
		_backPool.returnObject(this);
	}

}
