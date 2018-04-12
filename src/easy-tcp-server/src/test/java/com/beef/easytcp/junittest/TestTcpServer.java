package com.beef.easytcp.junittest;


import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.client.TcpClientConfig;
import com.beef.easytcp.client.pool.AsyncTcpClientPool;
import com.beef.easytcp.client.pool.PooledAsyncTcpClient;
import com.beef.easytcp.client.pool.PooledSyncTcpClient;
import com.beef.easytcp.client.pool.SyncTcpClientPool;
import com.beef.easytcp.server.TcpServer;
import com.beef.easytcp.server.TcpServerConfig;

/**
 * This class is made to test performance in different thread model.
 * It's a simple proxy and I use redis-benchmark to test performance, just because it's easy to test and evaluated by comparing with redis-server.
 * 
 * @author XingGu Liu
 *
 */
public class TestTcpServer {

	private final static Log logger = LogFactory.getLog(TestTcpServer.class);
	
	//private AtomicLong _logSeq = new AtomicLong(0);
	
	private SyncTcpClientPool _tcpClientPool;
	private AsyncTcpClientPool _asyncTcpClientPool;
	private TcpServer _server;
	
	protected static int SocketReceiveBufferSize = 1024 * 8;
	
	public static void main(String[] args) {
		int maxConnection = 10000;
		int ioThreadCount = 8;
		int readEventThreadCount = 32;
		//int writeEventThreadCount = ioThreadCount;
		//boolean isSyncInvokeDidReceivedMsg = false;
		int serverBufferSizeKB = 8;
		String hostRedirectTo = "127.0.0.1";
		int portRedirectTo = 6379;
		boolean useAsyncTcpClient = false;
		
		if(args.length > 0) {
			try {
				int i = 0;
				maxConnection = Integer.parseInt(args[i++]);
				ioThreadCount = Integer.parseInt(args[i++]);
				readEventThreadCount = Integer.parseInt(args[i++]);
				
				serverBufferSizeKB = Integer.parseInt(args[i++]);
				SocketReceiveBufferSize = 1024 * serverBufferSizeKB;
				
				hostRedirectTo = args[i++];
				portRedirectTo = Integer.parseInt(args[i++]);
				
				if(args[i++].equalsIgnoreCase("true")) {
					useAsyncTcpClient = true; 
				} else {
					useAsyncTcpClient = false;
				}
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
		System.out.println("MaxThread:" + maxConnection
				+ " ioThreadCount:" + ioThreadCount
				+ " readEventThreadCount:" + readEventThreadCount
				+ " SocketReceiveBufferSize:" + serverBufferSizeKB + " KB"
				+ " hostRedirectTo:" + hostRedirectTo
				+ " portRedirectTo:" + portRedirectTo 
				+ " useAsyncTcpClient:" + useAsyncTcpClient
				+ "------------------------------");
		
		TestTcpServer test = new TestTcpServer();
		test.startServer(
				maxConnection, ioThreadCount, readEventThreadCount, 
				hostRedirectTo, portRedirectTo, useAsyncTcpClient);
	}
	
	public void startServer(
			int maxConnection, int ioThreadCount, int readEventThreadCount, 
			String hostRedirectTo, int portRedirectTo,
			boolean useAsyncTcpClient) {
		try {
			//int maxConnection = (int) (1024 * 4);
			//int maxTcpClientPool = (int) (1024 * 4);
			
			TcpServerConfig serverConfig = new TcpServerConfig();
			serverConfig.setHost("127.0.0.1");
			serverConfig.setPort(6381);
			serverConfig.setConnectMaxCount(maxConnection);
			serverConfig.setConnectTimeout(1000);
			serverConfig.setSoTimeout(100);
			serverConfig.setConnectWaitCount(maxConnection);
			serverConfig.setSocketIOThreadCount(ioThreadCount);
			serverConfig.setSocketReceiveBufferSize(SocketReceiveBufferSize);
			serverConfig.setSocketSendBufferSize(SocketReceiveBufferSize);
			serverConfig.setReadEventThreadCount(readEventThreadCount);
			serverConfig.setWriteEventThreadCount(ioThreadCount);
			
			boolean isAllocateDirect = false;
			
			//Tcp Client -------------------------
			final TcpClientConfig tcpClientConfig = new TcpClientConfig();
			tcpClientConfig.setHost(hostRedirectTo);
			tcpClientConfig.setPort(portRedirectTo);
			tcpClientConfig.setConnectTimeoutMS(3000);
			tcpClientConfig.setSoTimeoutMS(1000);
			tcpClientConfig.setReceiveBufferSize(SocketReceiveBufferSize);
			tcpClientConfig.setSendBufferSize(SocketReceiveBufferSize);

			//tcp client
			int maxTcpClientPool = maxConnection;
			GenericObjectPoolConfig tcpClientPoolConfig = new GenericObjectPoolConfig();
			tcpClientPoolConfig.setMaxTotal(maxTcpClientPool);
			tcpClientPoolConfig.setMaxIdle(maxTcpClientPool / 5);
			tcpClientPoolConfig.setMaxWaitMillis(1000);

			_tcpClientPool = new SyncTcpClientPool(tcpClientPoolConfig, tcpClientConfig);
			
			GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
			byteBufferPoolConfig.setMaxIdle(maxTcpClientPool / 5);
			byteBufferPoolConfig.setMaxTotal(maxTcpClientPool);
			byteBufferPoolConfig.setMaxWaitMillis(1000);

			ByteBufferPool bufferPool = new ByteBufferPool(
					byteBufferPoolConfig, false, 1024 * 8); 
			_asyncTcpClientPool = new AsyncTcpClientPool(tcpClientPoolConfig, tcpClientConfig, bufferPool);
			//final TcpServerEventHandlerPool handlerPool = new TcpServerEventHandlerPool();
			
			if(useAsyncTcpClient) {
				_server = new TcpServer(
						serverConfig, isAllocateDirect, 
						new ITcpEventHandlerFactory() {

							@Override
							public ITcpEventHandler createHandler(int sessionId) {
								return (new TcpServerEventHandlerByAsyncClient(tcpClientConfig));
							}
						}
						);
			} else {
				_server = new TcpServer(
						serverConfig, isAllocateDirect, 
						new ITcpEventHandlerFactory() {
							
							@Override
							public ITcpEventHandler createHandler(int sessionId) {
								return (new TcpServerEventHandlerBySyncClient());
							}
						}
						//isSyncInvokeDidReceivedMsg
						);
			}

			
			_server.start();
			System.out.println("Start server -------------");
			
			
			//System.out.println("Shuting down server -------------");
			//Thread.sleep(300000);
			//server.shutdown();
			//System.out.println("Shutted down server -------------");
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * If use sync tcp client, then need to find whether received all reply through protocol.
	 * @author XingGu Liu
	 *
	 */
	private class TcpServerEventHandlerBySyncClient implements ITcpEventHandler {

		//private SyncTcpClient tcpClient;
		private PooledSyncTcpClient tcpClient;
		//private MessageList<IByteBuff> _remainingMsgs = new MessageList<IByteBuff>();
		
		
		public TcpServerEventHandlerBySyncClient() {
			//final int curConnectionCount = _server.getCurrentConnectionCount();
			//if((curConnectionCount % 50) == 0) 
//			{
//				logger.debug("Tcp Server connection count:" + curConnectionCount);
//			}
			
			//tcpClient = new SyncTcpClient(tcpConfig);
			tcpClient = _tcpClientPool.borrowObject();			
			
			/*
			try {
				tcpClient.disconnect();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			*/
			
//			try {
//				tcpClient.connect();
//			} catch(Throwable e) {
//				e.printStackTrace();
//			}
		}

		@Override
		public void didConnect(ITcpReplyMessageHandler replyMessageHandler,
				SocketAddress remoteAddress) {
		}

		@Override
		public void didDisconnect() {
			/*
			try {
				tcpClient.disconnect();
			} catch(Throwable e) {
				e.printStackTrace();
			}
			*/
			
			if(tcpClient != null) {
				try {
					tcpClient.returnToPool();
				} catch(Throwable e) {
					logger.error(null, e);
				}
			}
		}
		

		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler,
				MessageList<? extends IByteBuff> msgs) {
			try {
				Iterator<? extends IByteBuff> iter = msgs.iterator();
				IByteBuff msg;
				while(iter.hasNext()) {
					msg = iter.next();

					try {
						didReceiveMessage(replyMessageHandler, msg);
					} catch(Throwable e) {
						logger.error(null, e);
					}
				}
			} catch(Throwable e) {
				logger.error(null, e);
			} finally {
			}
		}

		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
			try {
				//send command ------------------------------------
				msg.getByteBuffer().flip();
				tcpClient.send(msg.getByteBuffer().array(), 
						msg.getByteBuffer().position(), msg.getByteBuffer().remaining());

				//receive response ------------------------------------
				//Thread.sleep(1);
				int msgLen = msg.getByteBuffer().remaining();
				String cmd = new String(msg.getByteBuffer().array(), msg.getByteBuffer().position(), msgLen);
				receiveAndReply(replyMessageHandler, cmd);
			} catch(SocketException e) {
				try {
					tcpClient.disconnect();
				} catch(Throwable e1) {
					logger.error(null, e1);
				}
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
		
		private void receiveAndReply(
				ITcpReplyMessageHandler replyMessageHandler, 
				String cmd) throws IOException {
			IByteBuff msg = replyMessageHandler.createBuffer();
			msg.getByteBuffer().clear();
			
			int rcvTotalLen = 0;
			int rcvLen = 0;
			
			//redis protocol
			int position = 0;
			int remaining = msg.getByteBuffer().limit();
			while(true)
			{
				try {
					rcvLen = tcpClient.receive(
							msg.getByteBuffer().array(), position, remaining);
				} catch(SocketTimeoutException e) {
					msg.destroy();
					return;
				}
				
				if(rcvLen <= 0) {
					msg.destroy();
					return;
				}
				
				position += rcvLen;
				remaining -=  rcvLen;
				rcvTotalLen += rcvLen;
				
				byte b = msg.getByteBuffer().array()[0];
				if(b == '-') {
					//Error reply
					break;
				} else if (b == '*') {
					//array
					if(msg.getByteBuffer().array()[rcvTotalLen - 2] == '\r' 
							&& msg.getByteBuffer().array()[rcvTotalLen - 1] == '\n') {
						int index = binarySearchReturnLine(
								msg.getByteBuffer().array(), 1, rcvTotalLen);
						if(index > 0) {
							int arraySize = Integer.parseInt(
									new String(msg.getByteBuffer().array(), 1, index - 1));
							int arrayCurSize = binaryCountRedisReplyElement(
									msg.getByteBuffer().array(), index + 2, rcvTotalLen);
							if(arraySize == arrayCurSize) {
								break;
							}
						}
					}
				} else if (b == ':') {
					//Integers, only one line
					break;
				} else if (b == '$') {
					//Bulk Strings
					if(msg.getByteBuffer().array()[rcvTotalLen - 2] == '\r' 
							&& msg.getByteBuffer().array()[rcvTotalLen - 1] == '\n') {
						int index = binarySearchReturnLine(
								msg.getByteBuffer().array(), 1, rcvTotalLen);
						if(index > 0) {
							int strLen = Integer.parseInt(
									new String(msg.getByteBuffer().array(), 1, index - 1));
							if(strLen == -1) {
								if(rcvTotalLen == 5) {
									break;
								} 
							} else {
								if((rcvTotalLen - index - 4) == strLen) {
									break;
								} 
							}
						}
					}
				} else if (b == '+') {
					//Simple Strings, only one line
					break;
				} else {
					logger.error("Unknown reply: " + (char)b);
					break;
				}
			}
						
			
			/*
			// Version 2
			int position = 0;
			int remaining = msg.getByteBuffer().limit();

			final long logSeq = _logSeq.incrementAndGet();
			int rcvCount = 0;
			tcpClient.connect();
			while(true) {
	    		try {
					rcvLen = tcpClient.getSocket().getInputStream().read(
							msg.getByteBuffer().array(), position, remaining);
	    		} catch(SocketTimeoutException e) {
	    			//nothing to read
	    			logger.debug(logSeq + " ------" + "TcpClient SocketTimeoutException"
	    					+ " rcvCount:" + rcvCount);
	    			break;
	    		}
	    		
	    		if(rcvLen < 0) {
	    			logger.debug(logSeq + " ------" + "TcpClient read til EOF"
	    					+ " rcvCount:" + rcvCount);
	    			break;
	    		}
	    		
	    		if(rcvLen > 0) {
	    			rcvCount++;
	    			
	    			remaining -= rcvLen;
	    			position += rcvLen;
	    			rcvTotalLen += rcvLen;
	    			
	    			if(msg.getByteBuffer().array()[position - 2] == '\r' 
	    					&& msg.getByteBuffer().array()[position - 1] == '\n') {
	    				//if(rcvCount > 1) 
//	    				{
//	    					logger.debug(logSeq + " ------" + "TcpClient read finish. "
//	    							+ " rcvLen:" + rcvLen + " rcvCount:" + rcvCount);
//	    				}
	    				break;
	    			} else if(remaining == 0) {
//	    				logger.debug(logSeq + " ------" + "TcpClient read til fullfill the buffer");
	    				break;
	    			} else {
//	    				logger.debug(logSeq + " ------" + "TcpClient read buffer not end with '\\r\\n'. cmd:" + cmd 
//	    						+ " rcvLen:" + rcvLen + " rcvCount:" + rcvCount);
	    			}
	    		}
			}
			*/

			/*
    		try {
				rcvLen = tcpClient.getSocket().getInputStream().read(
						msg.getByteBuffer().array(), position, remaining);
				if(rcvLen > 0) {
					rcvTotalLen += rcvLen;
				}
    		} catch(SocketTimeoutException e) {
    			//nothing to read
    			logger.warn("TcpClient SocketTimeoutException");
    		}
    		*/
			if(rcvTotalLen > 0) {
				msg.getByteBuffer().position(0);
				msg.getByteBuffer().limit(rcvTotalLen);
				replyMessageHandler.sendMessage(msg);
			} else {
				msg.destroy();
				return;
			}
			
			//Version 1 ----------------
			/* 
			try {
				rcvTotalLen = tcpClient.receive(
						msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
			} catch(SocketTimeoutException e) {
				msg.destroy();
				return;
			}
			if(rcvTotalLen > 0) {
				msg.getByteBuffer().position(0);
				msg.getByteBuffer().limit(rcvTotalLen);

				if(isResponseEndsCorrectly(msg.getByteBuffer().array(), msg.getByteBuffer().limit())) {
					if(_remainingMsgs.size() > 0) {
						MessageList<IByteBuff> msgs = _remainingMsgs.clone();
						msgs.add(msg);
						_remainingMsgs.clear();
						
						replyMessageHandler.sendMessage(msgs);
					} else {
						replyMessageHandler.sendMessage(msg);
					}
				} else {
					_remainingMsgs.add(msg);
				}
				
//				if(msg.getByteBuffer().array()[0] == '\r') {
//					System.out.println("didReceivedMsg() reply starts with '\\r'."
//							+ " rcvTotalLen:" + rcvTotalLen
//							+ " request:" + new String(requestMsg.getByteBuffer().array(), 0, msgLen)
//							+ " response:" + new String(msg.getByteBuffer().array(), 0, rcvTotalLen));
//				} else if(msg.getByteBuffer().array()[rcvTotalLen - 2] != '\r'
//						|| msg.getByteBuffer().array()[rcvTotalLen - 1] != '\n') {
//					System.out.println("didReceivedMsg() reply not ends with '\\r\\n'."
//							+ " rcvTotalLen:" + rcvTotalLen
//							+ " request:" + new String(requestMsg.getByteBuffer().array(), 0, msgLen)
//							+ " response:" + new String(msg.getByteBuffer().array(), 0, rcvTotalLen));
//				}
				
			}
			*/
		}

	}
	
	private static int binarySearchReturnLine(byte[] bytes, int start, int endIndex) {
		endIndex --;
		for(int offset = start; offset < endIndex; offset++) {
			if(bytes[offset] == '\r' && bytes[offset+1] == '\n') {
				return offset;
			}
		}
		
		return -1;
	}

	private static int binaryCountRedisReplyElement(byte[] bytes, int start, int endIndex) {
		int cnt = 0;
		int offset = start;
		byte b;
		int indexOfReturnLine;
		while(offset < endIndex) {
			b = bytes[offset];
			if(b == '+' || b == '-' || b == ':') {
				indexOfReturnLine = binarySearchReturnLine(bytes, offset, endIndex);
				if(indexOfReturnLine >= 0) {
					cnt ++;
					offset = indexOfReturnLine + 2;
				} else {
					break;
				}
			} else if (b == '$') {
				indexOfReturnLine = binarySearchReturnLine(bytes, offset, endIndex);
				if(indexOfReturnLine >= 0) {
					int strLen = Integer.parseInt(
							new String(bytes, offset + 1, indexOfReturnLine - offset - 1));
					if(strLen == -1) {
						offset = indexOfReturnLine + 2;
						cnt ++;
					} else {
						offset = indexOfReturnLine + 2 + strLen + 2;
						if(offset <= endIndex 
								&& bytes[offset - 2] == '\r' && bytes[offset - 1] == '\n') {
							cnt ++;
						} else {
							break;
						}
					}
				} else {
					break;
				}
			}
			
		}
		
		return cnt;
	}
	
	/*
	private static boolean isResponseEndsCorrectly(byte[] buffer, int contentLen) {
		if(contentLen < 2) {
			return false;
		} else {
			if(buffer[contentLen - 2] == '\r' && buffer[contentLen - 1] == '\n') {
				return true;
			} else {
				return false;
			}
		}
	}
	*/
	
	//Async client is more complex than sync client.
	private class TcpServerEventHandlerByAsyncClient implements ITcpEventHandler {
		private PooledAsyncTcpClient tcpClient;
		//private AsyncTcpClient tcpClient;
		private ITcpReplyMessageHandler _replyMessageHandler;
		
		public TcpServerEventHandlerByAsyncClient(
				TcpClientConfig tcpClientConfig) {

			//logger.debug("Tcp Server connection count:" + _server.getCurrentConnectionCount());
			
			tcpClient = _asyncTcpClientPool.borrowObject();
			//tcpClient = new AsyncTcpClient(tcpClientConfig, SocketReceiveBufferSize);
			
			tcpClient.setEventHandler(new ClientEventHandler());
			
			//logger.debug("AsyncTcpClient connect done");
		}
		
		@Override
		public void didConnect(ITcpReplyMessageHandler replyMessageHandler,
				SocketAddress remoteAddress) {
			try {
				//logger.debug("AsyncTcpClient connecting");
				tcpClient.connect();
				//logger.debug("AsyncTcpClient isConnected:" + tcpClient.isConnected());
			} catch (IOException e) {
				logger.error(null, e);
			}
		}

		@Override
		public void didDisconnect() {
			//logger.info("TcpClient disconnectting ......");
			//tcpClient.disconnect();

			try {
				tcpClient.returnToPool();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler,
				MessageList<? extends IByteBuff> msgs) {
			_replyMessageHandler = replyMessageHandler;
			
			Iterator<? extends IByteBuff> iter = msgs.iterator();
			IByteBuff msg;
			while(iter.hasNext()) {
				msg = iter.next();

				didReceiveMessage(replyMessageHandler, msg);
			}
		}

		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
			try {
				_replyMessageHandler = replyMessageHandler;

				msg.getByteBuffer().flip();
				
			 	IByteBuff sendBuff = replyMessageHandler.createBuffer();
			 	System.arraycopy(
			 			msg.getByteBuffer().array(), msg.getByteBuffer().position(), 
			 			sendBuff.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
			 	sendBuff.getByteBuffer().position(0);
			 	sendBuff.getByteBuffer().limit(msg.getByteBuffer().limit());
			 	
			 	//logger.debug("tcp server received msg");
				tcpClient.send(sendBuff);
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}

		private class ClientEventHandler implements ITcpEventHandler {

			@Override
			public void didConnect(ITcpReplyMessageHandler replyMessageHandler,
					SocketAddress remoteAddress) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void didDisconnect() {
			}
			
			@Override
			public void didReceiveMessage(
					ITcpReplyMessageHandler replyMessageHandler,
					MessageList<? extends IByteBuff> msgs) {
				Iterator<? extends IByteBuff> iter = msgs.iterator();
				IByteBuff msg;
				while(iter.hasNext()) {
					msg = iter.next();

					this.didReceiveMessage(replyMessageHandler, msg);
				}

				//TcpServerEventHandlerByAsyncClient.this._replyMessageHandler.sendMessage(msgs);
			}

			@Override
			public void didReceiveMessage(
					ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
				try {
					msg.getByteBuffer().flip();
					
				 	IByteBuff sendBuff = TcpServerEventHandlerByAsyncClient.this._replyMessageHandler.createBuffer();
				 	System.arraycopy(
				 			msg.getByteBuffer().array(), msg.getByteBuffer().position(), 
				 			sendBuff.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
				 	sendBuff.getByteBuffer().position(0);
				 	sendBuff.getByteBuffer().limit(msg.getByteBuffer().limit());

				 	//logger.debug("tcp client received msg");
					TcpServerEventHandlerByAsyncClient.this._replyMessageHandler.sendMessage(sendBuff);
				} catch(Throwable e) {
					logger.error(null, e);
				} 
			}
			
		}
	}

}
