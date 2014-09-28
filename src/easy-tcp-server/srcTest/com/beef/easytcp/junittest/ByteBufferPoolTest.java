package com.beef.easytcp.junittest;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.Test;

import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.buffer.ByteBufferPoolFactory;
import com.beef.easytcp.base.buffer.PooledByteBuffer;

public class ByteBufferPoolTest {

	@Test
	public void test() {
		int bufferByteSize = 8;
		boolean _isAllocateDirect = false;
		ByteBufferPoolFactory byteBufferPoolFactory = new ByteBufferPoolFactory(
				_isAllocateDirect, bufferByteSize);
		
		int poolMaxActive = 4;
		GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
		byteBufferPoolConfig.setMaxIdle(2);
		/* old version
		byteBufferPoolConfig.setMaxActive(_PoolMaxActive);
		byteBufferPoolConfig.setMaxWait(_PoolMaxWait);
		*/
		byteBufferPoolConfig.setMaxTotal(poolMaxActive);
		byteBufferPoolConfig.setMaxWaitMillis(10);
		
		//byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
		//byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);

		ByteBufferPool _bufferPool = new ByteBufferPool(
				byteBufferPoolConfig, _isAllocateDirect, bufferByteSize);
		
		List<PooledByteBuffer> bufferList = new ArrayList<PooledByteBuffer>();
		for(int i = 0; i < poolMaxActive; i++) {
			bufferList.add(_bufferPool.borrowObject());
		}
		
		for(int i = 0; i < bufferList.size(); i++) {
			bufferList.get(i).returnToPool();
		}
		
		for(int i = 0; i < poolMaxActive + 2; i++) {
			_bufferPool.borrowObject();
		}
		
	}

}
