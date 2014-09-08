package com.beef.easytcp.server.junittest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.Test;

import com.beef.easytcp.server.base.ChannelByteBuffer;
import com.beef.easytcp.server.base.ChannelByteBufferPoolFactory;

public class Test1 {

	@Test
	public void test3() {
		try {
			ExecutorService threadPool = Executors.newFixedThreadPool(4);
			
			threadPool.execute(new TestThread());
			threadPool.execute(new TestThread());
			threadPool.execute(new TestThread());
			threadPool.execute(new TestThread());
			
			Thread.sleep(3000);
			threadPool.shutdownNow();
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
	
	public void test2() {
		AtomicInteger i = new AtomicInteger(Integer.MAX_VALUE);
		int n = i.incrementAndGet();
		System.out.println("n:" + n + " mod:" + (n % 5));
	}
	
	public void test1() {
		try {
			int bufferSize = 1024 * 4;
			int maxConnectCount = 8;
			ChannelByteBufferPoolFactory byteBufferPoolFactory = new ChannelByteBufferPoolFactory(
					false, 
					bufferSize, 
					bufferSize
					);
			GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
			byteBufferPoolConfig.setMaxIdle(maxConnectCount);
			/* old version
			byteBufferPoolConfig.setMaxActive(_PoolMaxActive);
			byteBufferPoolConfig.setMaxWait(_PoolMaxWait);
			*/
			byteBufferPoolConfig.setMaxTotal(maxConnectCount);
			byteBufferPoolConfig.setMaxWaitMillis(500);
			
			//byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
			//byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);
			
			GenericObjectPool<ChannelByteBuffer> channelByteBufferPool = new GenericObjectPool<ChannelByteBuffer>(
					byteBufferPoolFactory, byteBufferPoolConfig);
			
			List<ChannelByteBuffer> byteBufferList = new ArrayList<ChannelByteBuffer>();
			for(int i = 0; i < maxConnectCount; i++) {
				ChannelByteBuffer byteBuffer = channelByteBufferPool.borrowObject();
				byteBufferList.add(byteBuffer);
			}
			
			for(int i = 0; i < byteBufferList.size(); i++) {
				channelByteBufferPool.returnObject(byteBufferList.get(i));
			}
			
			System.out.println("pool created count:" + channelByteBufferPool.getCreatedCount());
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
	
	protected class TestThread extends Thread {
		
		protected void didStop() {
			System.out.println("TestThread didStop()");
		}

		@Override
		public void run() {
			System.out.println("Start >>>>>>>>>>>>>>>>>>>");
			
			while(true) {
				System.out.println("run loop -----");
			
				try {
					Thread.sleep(1);
				} catch(InterruptedException e) {
					System.out.println("Stopped <<<<<<<<<<<<<<<<<<<< isAlive:" + this.isAlive());
					didStop();
				}
			}
		}
		
	}
}
