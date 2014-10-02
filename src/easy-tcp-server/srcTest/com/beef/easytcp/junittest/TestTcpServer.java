package com.beef.easytcp.junittest;


import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.client.AsyncTcpClient;
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

	private final static Logger logger = Logger.getLogger(TestTcpServer.class);
	
	private SyncTcpClientPool _tcpClientPool;
	private AsyncTcpClientPool _asyncTcpClientPool;
	
	protected static int SocketReceiveBufferSize = 1024 * 32;
	
	public static void main(String[] args) {
		int maxConnection = 10000;
		int ioThreadCount = 8;
		int readEventThreadCount = 32;
		//int writeEventThreadCount = ioThreadCount;
		//boolean isSyncInvokeDidReceivedMsg = false;
		int serverBufferSizeKB = 32;
		int SocketReceiveBufferSize = 1024 * serverBufferSizeKB;
		String hostRedirectTo = "127.0.0.1";
		int portRedirectTo = 6379;
		
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
				+ "------------------------------");
		
		TestTcpServer test = new TestTcpServer();
		test.startServer(
				maxConnection, ioThreadCount, readEventThreadCount, 
				hostRedirectTo, portRedirectTo);
	}
	
	public void startServer(
			int maxConnection, int ioThreadCount, int readEventThreadCount, 
			String hostRedirectTo, int portRedirectTo) {
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
			tcpClientConfig.setConnectTimeoutMS(1000);
			tcpClientConfig.setSoTimeoutMS(100);
			tcpClientConfig.setReceiveBufferSize(SocketReceiveBufferSize);
			tcpClientConfig.setSendBufferSize(SocketReceiveBufferSize);

			//tcp client
			int maxTcpClientPool = maxConnection;
			GenericObjectPoolConfig tcpClientPoolConfig = new GenericObjectPoolConfig();
			tcpClientPoolConfig.setMaxTotal(maxTcpClientPool);
			tcpClientPoolConfig.setMaxIdle(maxTcpClientPool / 5);
			tcpClientPoolConfig.setMaxWaitMillis(1000);

			_tcpClientPool = new SyncTcpClientPool(tcpClientPoolConfig, tcpClientConfig);
			_asyncTcpClientPool = new AsyncTcpClientPool(tcpClientPoolConfig, tcpClientConfig, 4);
			//final TcpServerEventHandlerPool handlerPool = new TcpServerEventHandlerPool();
			
			TcpServer server = new TcpServer(
					serverConfig, isAllocateDirect, 
					new ITcpEventHandlerFactory() {

						@Override
						public ITcpEventHandler createHandler(int sessionId) {
							return (new TcpServerEventHandlerByAsyncClient(
									tcpClientConfig));
						}
					}
					);
			/*
			TcpServer server = new TcpServer(
					serverConfig, isAllocateDirect, 
					new ITcpEventHandlerFactory() {
						
						@Override
						public ITcpEventHandler createHandler(int sessionId) {
							return (new TcpServerEventHandlerBySyncClient());
						}
					}
					//isSyncInvokeDidReceivedMsg
					);
			*/
			
			server.start();
			System.out.println("Start server -------------");
			
			
			//System.out.println("Shuting down server -------------");
			//Thread.sleep(300000);
			//server.shutdown();
			//System.out.println("Shutted down server -------------");
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
	
	private class TcpServerEventHandlerBySyncClient implements ITcpEventHandler {

		//private SyncTcpClient tcpClient;
		private PooledSyncTcpClient tcpClient;
		private MessageList<IByteBuff> _remainingMsgs = new MessageList<IByteBuff>();
		
		public TcpServerEventHandlerBySyncClient() {
			
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
//				try {
//					tcpClient.returnToPool();
//				} catch(Throwable e) {
//					logger.error(null, e);
//				}
			}
		}

		@Override
		public void didReceiveMessage(
				ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
			try {
				//send command ------------------------------------
				int msgLen = msg.getByteBuffer().remaining();
				msg.getByteBuffer().flip();
				tcpClient.send(msg.getByteBuffer().array(), 
						msg.getByteBuffer().position(), msg.getByteBuffer().remaining());

				//receive response ------------------------------------
				receiveAndReply(replyMessageHandler, msg, msgLen);
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
		
		private void receiveAndReply(ITcpReplyMessageHandler replyMessageHandler, IByteBuff requestMsg, int msgLen) throws IOException {
			IByteBuff msg = replyMessageHandler.createBuffer();
			
			msg.getByteBuffer().clear();
			int rcvTotalLen;
			try {
				rcvTotalLen = tcpClient.receive(
						msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
			} catch(SocketTimeoutException e) {
				msg.destroy();
				return;
			}
			
			//int receiveLen = tcpClient.receiveUntilFillUpBufferOrEnd(msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
			
			/*
			int rcvTotalLen = 0;
			int rcvLen;
			int position = 0;
			int remaining = msg.getByteBuffer().limit();
			tcpClient.connect();
			while(true) {
	    		try {
					rcvLen = tcpClient.getSocket().getInputStream().read(
							msg.getByteBuffer().array(), position, remaining);
	    		} catch(SocketTimeoutException e) {
	    			//nothing to read
	    			break;
	    		}
	    		
	    		if(rcvLen < 0) {
	    			break;
	    		}
	    		
	    		if(rcvLen > 0) {
	    			remaining -= rcvLen;
	    			position += rcvLen;
	    			rcvTotalLen += rcvLen;
	    			
	    			if(msg.getByteBuffer().array()[position - 2] == '\r' 
	    					&& msg.getByteBuffer().array()[position - 1] == '\n') {
	    				break;
	    			} else if(remaining == 0) {
	    				break;
	    			}
	    		}
			}
			*/
			
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
				
				/*
				if(msg.getByteBuffer().array()[0] == '\r') {
					System.out.println("didReceivedMsg() reply starts with '\\r'."
							+ " rcvTotalLen:" + rcvTotalLen
							+ " request:" + new String(requestMsg.getByteBuffer().array(), 0, msgLen)
							+ " response:" + new String(msg.getByteBuffer().array(), 0, rcvTotalLen));
				} else if(msg.getByteBuffer().array()[rcvTotalLen - 2] != '\r'
						|| msg.getByteBuffer().array()[rcvTotalLen - 1] != '\n') {
					System.out.println("didReceivedMsg() reply not ends with '\\r\\n'."
							+ " rcvTotalLen:" + rcvTotalLen
							+ " request:" + new String(requestMsg.getByteBuffer().array(), 0, msgLen)
							+ " response:" + new String(msg.getByteBuffer().array(), 0, rcvTotalLen));
				}
				*/
				
			}
		}

	}

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
	
	//I think async client is slower and more complex than sync client, so it not use any more
	private class TcpServerEventHandlerByAsyncClient implements ITcpEventHandler {
		private PooledAsyncTcpClient tcpClient;
		private ITcpReplyMessageHandler _replyMessageHandler;
		
		public TcpServerEventHandlerByAsyncClient(
				TcpClientConfig tcpClientConfig) {
			//tcpClient = new AsyncTcpClient(tcpClientConfig, SocketReceiveBufferSize);
			tcpClient = _asyncTcpClientPool.borrowObject();
			
			try {
				tcpClient.setEventHandler(new ClientEventHandler());
				tcpClient.connect();
				tcpClient.waitConnect(1000);
			} catch (IOException e) {
				logger.error(null, e);
			}
		}
		
		@Override
		public void didConnect(ITcpReplyMessageHandler replyMessageHandler,
				SocketAddress remoteAddress) {
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

					msg.getByteBuffer().flip();
				}

				TcpServerEventHandlerByAsyncClient.this._replyMessageHandler.sendMessage(msgs);
			}

			@Override
			public void didReceiveMessage(
					ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg) {
				try {
					msg.getByteBuffer().flip();
					
				 	IByteBuff sendBuff = replyMessageHandler.createBuffer();
				 	System.arraycopy(
				 			msg.getByteBuffer().array(), msg.getByteBuffer().position(), 
				 			sendBuff.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
				 	sendBuff.getByteBuffer().position(0);
				 	sendBuff.getByteBuffer().limit(msg.getByteBuffer().limit());
				 	
					TcpServerEventHandlerByAsyncClient.this._replyMessageHandler.sendMessage(sendBuff);
				} catch(Throwable e) {
					logger.error(null, e);
				} 
			}
			
		}
	}

}
