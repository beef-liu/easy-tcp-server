package com.beef.easytcp.asyncserver.test;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.beef.easytcp.asyncclient.AsyncTcpClient;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuffer;
import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.client.TcpClientConfig;

public class TestAsyncTcpClient {
	private final static Logger logger = Logger.getLogger(TestAsyncTcpClient.class);

	@Test
	public void test1() {
		final int receiveBufferSize = 16 * 1024;
		
        TcpClientConfig tcpClientConfig = new TcpClientConfig();
        tcpClientConfig.setHost("127.0.0.1");
        tcpClientConfig.setPort(6379);
        tcpClientConfig.setConnectTimeoutMS(3000);
        tcpClientConfig.setSoTimeoutMS(1000);
        tcpClientConfig.setReceiveBufferSize(receiveBufferSize);
        tcpClientConfig.setSendBufferSize(receiveBufferSize);
        tcpClientConfig.setTcpNoDelay(true);
		
        AsyncTcpClient client = null;
        try {
    		client = new AsyncTcpClient(
    				tcpClientConfig, 
    				new IByteBuffProvider() {
    					
    					@Override
    					public void close() throws IOException {
    						//do nothing
    					}
    					
    					@Override
    					public IByteBuff createBuffer() {
    						return new ByteBuff(ByteBuffer.allocate(receiveBufferSize));
    					}
    				});
    		client.setEventHandler(
    				new ITcpEventHandler() {
						
						@Override
						public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
							msg.getByteBuffer().flip();
							
							String response = new String(
									msg.getByteBuffer().array(), 
									msg.getByteBuffer().position(), 
									msg.getByteBuffer().remaining()
									);
							logger.debug("tcp received --> " + response);
						}
						
						@Override
						public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, MessageList<? extends IByteBuff> msgs) {
							for(IByteBuff msg : msgs) {
								didReceiveMessage(replyMessageHandler, msg);
							}
						}
						
						@Override
						public void didDisconnect() {
							logger.debug("didDisconnect");
						}
						
						@Override
						public void didConnect(ITcpReplyMessageHandler replyMessageHandler, SocketAddress remoteAddress) {
							logger.debug("didConnect");
						}
					});
    		
    		ExecutorService threadPool = Executors.newFixedThreadPool(5);
    	
    		final int testCount = 500;
    		for(int i = 0; i < testCount; i++) {
    			threadPool.execute(new TestThread(client));
    		}
    		
    		Thread.sleep(10 * 1000);
        } catch (Throwable e) {
        	logger.error(null, e);
        } finally {
        	try {
            	if(client != null) {
            		client.disconnect();
            	}
            } catch (Throwable e) {
            	logger.error(null, e);
        	}
        }
	}
	
	private static class TestThread implements Runnable {
		private final static AtomicInteger _threadNumSeed = new AtomicInteger(0);
		
		private final AsyncTcpClient _client;
		private final int _threadNum;
		
		public TestThread(AsyncTcpClient client) {
			_client = client;
			_threadNum = _threadNumSeed.incrementAndGet();
		}
		
		@Override
		public void run() {
			try {
				String command = "ping" + "\r\n";
				_client.send(new AsyncWriteEvent4ByteBuffer(ByteBuffer.wrap(command.getBytes())));
				logger.debug("TestThread[" + _threadNum + "] added send event. contents:" + command);
            } catch (Throwable e) {
            	logger.error(null, e);
			}
		}
	}
}
