package com.beef.easytcp.junittest;

import java.net.SocketAddress;
import java.util.Iterator;

import org.junit.Test;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.client.AsyncTcpClient;
import com.beef.easytcp.client.SyncTcpClient;
import com.beef.easytcp.client.TcpClientConfig;

public class TestTcpClient {
	private static String requestStr = "PING\r\n";
	private static byte[] requestBytes = requestStr.getBytes();
	
	private static String expectResponse = "+PONG\r\n";
	private static byte[] expectResponseBytes = expectResponse.getBytes();
	
	private static TcpClientConfig createConfig() {
		int SocketReceiveBufferSize = 1024 * 4;
		
		final TcpClientConfig tcpClientConfig = new TcpClientConfig();
		tcpClientConfig.setHost("127.0.0.1");
		tcpClientConfig.setPort(6379);
		tcpClientConfig.setConnectTimeoutMS(500);
		tcpClientConfig.setSoTimeoutMS(100);
		tcpClientConfig.setReceiveBufferSize(SocketReceiveBufferSize);
		tcpClientConfig.setSendBufferSize(SocketReceiveBufferSize);
		
		return tcpClientConfig;
	}

	@Test
	public void testSyncTcpClient() {
		final TcpClientConfig tcpConfig = createConfig();
		SyncTcpClient client = new SyncTcpClient(tcpConfig);

		try {
			client.connect();
			
			byte[] rcvBuff = new byte[32];
			int rcvLen;
			final int loopCount = 10000 * 4;
			int failedCount = 0;
			long startTime = System.currentTimeMillis();
			
			for(int i = 0; i < loopCount; i++) {
				try {
					client.send(requestBytes, 0, requestBytes.length);
					
					rcvLen = client.receive(rcvBuff, 0, rcvBuff.length);
					
					//System.out.println("[" + new String(rcvBuff, 0, rcvLen) + "]");
					if(rcvLen == expectResponseBytes.length
							&& isBytesEqual(rcvBuff, expectResponseBytes, expectResponseBytes.length)) {
						
					} else {
						System.out.println("wrong response");
						failedCount++;
					}
				} catch(Throwable e) {
					e.printStackTrace();
					failedCount++;
				}
			}
			
			long costTime = System.currentTimeMillis() - startTime;
			System.out.println("SyncTcpClient cost:" + costTime + " ms " + "Failed count:" + failedCount);
		} catch(Throwable e) {
			e.printStackTrace();
		} finally {
			try {
				client.disconnect();
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		testASyncTcpClient();
	}
	
	public static void testASyncTcpClient() {
		final TcpClientConfig tcpConfig = createConfig();

		ITcpEventHandlerFactory eventHandlerFactory = new ITcpEventHandlerFactory() {
			
			@Override
			public ITcpEventHandler createHandler(int sessionId) {
				return new MyTcpClinetEventHandler();
			}
		};
		
		AsyncTcpClient client = new AsyncTcpClient(tcpConfig, 0, eventHandlerFactory, 64);

		try {
			client.connect();
			
			int waitTime = 0;
			while(waitTime < 100) {
				
				if(client.isConnected()) {
					break;
				}
				
				Thread.sleep(1);
				waitTime ++;
			}
			
			System.out.println("AsyncTcpClient isConnected:" + client.isConnected());

			int loopCount = 10000 * 4;
			loopCount = 100;
			
			int failedCount = 0;
			long startTime = System.currentTimeMillis();
			

			/*
			ByteBuffer sendBuff = ByteBuffer.allocate(32);
			for(int i = 0; i < loopCount; i++) {
				try {
					sendBuff.clear();
					System.arraycopy(requestBytes, 0, sendBuff.array(), 0, requestBytes.length);
					sendBuff.position(requestBytes.length);
					client.send(sendBuff);
					
				} catch(Throwable e) {
					e.printStackTrace();
					failedCount++;
				}
			}
			*/

			Thread.sleep(4000);
			
			long costTime = ((MyTcpClinetEventHandler) client.getEventHandler()).getLastReceiveTime()
					- startTime;

			loopCount = ((MyTcpClinetEventHandler) client.getEventHandler()).getSentCount();
			int successCount = ((MyTcpClinetEventHandler) client.getEventHandler()).getSuccessCount();
			failedCount = loopCount - successCount;
			System.out.println("AsyncTcpClient cost:" + costTime + " ms"
					+ "; sentCount:" + loopCount
					+ "; Failed count:" + failedCount);
		} catch(Throwable e) {
			e.printStackTrace();
		} finally {
			try {
				client.disconnect();
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
	}
	
	private static class MyTcpClinetEventHandler implements ITcpEventHandler {
		private volatile int _successCount = 0;
		private long _lastReceiveTime = 0;
		private int sentMaxCount = 100;
		private int sentCount = 0;

		public int getSentCount() {
			return sentCount;
		}

		//ByteBuffer sendBuff = ByteBuffer.allocate(32);
		
		public MyTcpClinetEventHandler() {
		}
		
		public int getSuccessCount() {
			return _successCount;
		}

		public long getLastReceiveTime() {
			return _lastReceiveTime;
		}

		@Override
		public void didConnect(ITcpReplyMessageHandler replyMessageHandler,
				SocketAddress remoteAddress) {
			sendRequest(replyMessageHandler);
		}

		@Override
		public void didDisconnect() {
			// TODO Auto-generated method stub
			
		}
		
		private void sendRequest(ITcpReplyMessageHandler replyMessageHandler) {
			try {
				IByteBuff sendBuff = replyMessageHandler.createBuffer();
				sendBuff.getByteBuffer().clear();
				
				System.arraycopy(requestBytes, 0, sendBuff.getByteBuffer().array(), 0, requestBytes.length); 
				sendBuff.getByteBuffer().limit(requestBytes.length);
				
				replyMessageHandler.sendMessage(sendBuff);
			} catch(Throwable e) {
				e.printStackTrace();
			} finally {
				sentCount++;
			}
		}

		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler,
				MessageList<? extends IByteBuff> msgs) {
			Iterator<? extends IByteBuff> iter = msgs.iterator();
			
			while(iter.hasNext()) {
				didReceiveMessage(replyMessageHandler, iter.next());
			}
		}
		
		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
			System.out.println("didReceivedMsg() ------");

			_lastReceiveTime = System.currentTimeMillis();
			
			msg.getByteBuffer().flip();
			if(msg.getByteBuffer().remaining() == expectResponseBytes.length
					&& isBytesEqual(msg.getByteBuffer().array(), expectResponseBytes, expectResponseBytes.length)) {
				_successCount++;
				System.out.println("didReceivedMsg() _successCount:" + _successCount);
			}
			
			sendRequest(replyMessageHandler);
		}
		
	}
	
	private static boolean isBytesEqual(byte[] b1, byte[] b2, int compareLen) {
		for(int i = 0; i < compareLen; i++) {
			if(b1[i] != b2[i]) {
				return false;
			}
		}
		
		return true;
	}
	
}
