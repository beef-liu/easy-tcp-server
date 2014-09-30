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
import com.beef.easytcp.client.TcpClientConfig;
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
	protected static int SocketReceiveBufferSize = 1024 * 32;
	
	public static void main(String[] args) {
		int maxConnection = 10000;
		int ioThreadCount = 4;
		int readEventThreadCount = 16;
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
			serverConfig.setSoTimeout(1000);
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
			//final TcpServerEventHandlerPool handlerPool = new TcpServerEventHandlerPool();
			
			/*
			TcpServer server = new TcpServer(
					serverConfig, isAllocateDirect, 
					new ITcpEventHandlerFactory() {
						
						@Override
						public AbstractTcpEventHandler createHandler(int sessionId,
								//SelectionKey readKey, 
								SelectionKey writeKey) {
							return (new TcpServerEventHandlerByAsyncClient(
									tcpClientConfig, sessionId, 
									//readKey, 
									writeKey));
						}
					},
					isSyncInvokeDidReceivedMsg
					);
			*/
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
			try {
				tcpClient.disconnect();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
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
			int rcvTotalLen = tcpClient.receive(
					msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
			
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

		private boolean isResponseEndsCorrectly(byte[] buffer, int contentLen) {
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

	}
	
	/* I think async client is slower and more complex than sync client, so it not use any more
	private class TcpServerEventHandlerByAsyncClient extends AbstractTcpEventHandler<PooledByteBuffer> {
		private AsyncTcpClient tcpClient;
		
		public TcpServerEventHandlerByAsyncClient(
				TcpClientConfig tcpClientConfig,
				int sessionId,
				//SelectionKey readKey,
				SelectionKey writeKey) {
			super(sessionId, 
					//readKey, 
					writeKey);
			
			tcpClient = new AsyncTcpClient(tcpClientConfig, sessionId, 
					new ITcpEventHandlerFactory<ByteBuff>() {
						
						@Override
						public AbstractTcpEventHandler<ByteBuff> createHandler(int sessionId,
								//SelectionKey readKey, 
								SelectionKey writeKey) {
							return new ClientEventHandler(sessionId, 
									//readKey, 
									writeKey);
						}
					});
			
			try {
				tcpClient.connect();
				
				long waitTime = 0;
				while(waitTime <= 500) {
					if(tcpClient.isConnected()) {
						break;
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						logger.error(null, e);
					}
					
					waitTime ++;
				}
				if(!tcpClient.isConnected()) {
					logger.debug("TcpClient connect failed");
				} else {
					//logger.debug("TcpClient connect succeeded");
				}
			} catch (IOException e) {
				logger.error(null, e);
			}
		}
		
		@Override
		public void didConnect() {
		}

		@Override
		public void didDisconnect() {
			//logger.info("TcpClient disconnectting ......");
			tcpClient.disconnect();
		}

		@Override
		public void didReceivedMsg(MessageList<PooledByteBuffer> messages) {
			Iterator<PooledByteBuffer> iter = messages.iterator();
			PooledByteBuffer msg;
			while(iter.hasNext()) {
				msg = iter.next();

				didReceivedMsg(msg);
			}
		}

		@Override
		public void didReceivedMsg(PooledByteBuffer msg) {
			try {
				msg.getByteBuffer().flip();
				tcpClient.send(msg.getByteBuffer());
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
	
		
		private class ClientEventHandler extends AbstractTcpEventHandler<ByteBuff> {

			public ClientEventHandler(int sessionId, 
					//SelectionKey readKey, 
					SelectionKey writeKey) {
				super(sessionId, 
						//readKey, 
						writeKey);
			}
			
			@Override
			public void didConnect() {
			}

			@Override
			public void didDisconnect() {
			}

			@Override
			public void didReceivedMsg(MessageList<ByteBuff> messages) {
				Iterator<ByteBuff> iter = messages.iterator();
				ByteBuff msg;
				while(iter.hasNext()) {
					msg = iter.next();

					this.didReceivedMsg(msg);
				}
			}

			@Override
			public void didReceivedMsg(ByteBuff msg) {
				try {
					msg.getByteBuffer().flip();
					TcpServerEventHandlerByAsyncClient.this.writeMessage(msg.getByteBuffer());
				} catch(Throwable e) {
					logger.error(null, e);
				} 
			}
			
		}
		
	}
	*/
}
