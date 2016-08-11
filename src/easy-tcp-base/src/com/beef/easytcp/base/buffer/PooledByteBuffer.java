package com.beef.easytcp.base.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.IPool;
import com.beef.easytcp.base.IPooledObject;

public class PooledByteBuffer extends ByteBuff implements IPooledObject {
	protected IPool<PooledByteBuffer> _backPool = null;
	

	private AtomicBoolean _deferredDestroy = new AtomicBoolean(false);


	
	public PooledByteBuffer(boolean isAllocateDirect, int bufferByteSize) {
		super(isAllocateDirect, bufferByteSize);
	}
	
	public PooledByteBuffer(ByteBuffer byteBuff) {
		super(byteBuff);
	}
	
	@Override
	public void setPoolReference(IPool<? extends IPooledObject> pool) {
		if(_backPool != pool) {
			//_backPool = null;
			_backPool = (IPool<PooledByteBuffer>) pool;
		}

		_deferredDestroy.set(false);
	}

	@Override
	public void returnToPool() {
		/* deprecated 
		synchronized (this) {
			if(_backPool != null) {
				final IPool<PooledByteBuffer> pool = _backPool; 
				_backPool = null;
				pool.returnObject(this);
			}
		}
		*/
		_backPool.returnObject(this);
	}
	
	@Override
	public ByteBuffer getByteBuffer() {
		/* deprecated
		if(_backPool == null) {
			throw new RuntimeException("PooledByteBuffer has already been returned to pool.");
		}
		*/
		
		return super.getByteBuffer();
	}

	@Override
	public void destroy() {
		returnToPool();
	}

    /**
     * if true, destroy() should be invoked by whom invoked setDeferredDestroy().
     * @return
     */
    public boolean isDeferredDestroy() {
        return _deferredDestroy.get();
    }

    public void setDeferredDestroy(boolean deferredDestroy) {
        _deferredDestroy.set(deferredDestroy);
    }
}
