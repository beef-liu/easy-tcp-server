package com.beef.easytcp.junittest;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.buffer.PooledByteBuffer;
import com.beef.easytcp.base.handler.AbstractTcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.base.handler.SelectionKeyWrapper;
import com.beef.easytcp.base.handler.SessionObj;
import com.beef.easytcp.client.AsyncTcpClient;
import com.beef.easytcp.client.SyncTcpClient;
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
	
	private final static int SocketReceiveBufferSize = 1024 * 64;

	private SyncTcpClientPool _tcpClientPool;
	
	public static void main(String[] args) {
		int maxConnection = 10000;
		int ioThreadCount = 4;
		int workThreadCount = 2048;
		boolean isSyncInvokeDidReceivedMsg = false;
		String hostRedirectTo = "127.0.0.1";
		int portRedirectTo = 6379;
		
		if(args.length > 0) {
			try {
				int i = 0;
				maxConnection = Integer.parseInt(args[i++]);
				ioThreadCount = Integer.parseInt(args[i++]);
				workThreadCount = Integer.parseInt(args[i++]);
				
				if(args[i++].equals("true")) {
					isSyncInvokeDidReceivedMsg = true;
				} else {
					isSyncInvokeDidReceivedMsg = false;
				}
				
				hostRedirectTo = args[i++];
				portRedirectTo = Integer.parseInt(args[i++]);
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
		System.out.println("MaxThread:" + maxConnection
				+ " ioThreadCount:" + ioThreadCount
				+ " workThreadCount:" + workThreadCount
				+ " isSyncInvokeDidReceivedMsg:" + isSyncInvokeDidReceivedMsg
				+ " hostRedirectTo:" + hostRedirectTo
				+ " portRedirectTo:" + portRedirectTo 
				+ "------------------------------");
		
		TestTcpServer test = new TestTcpServer();
		test.startServer(
				maxConnection, ioThreadCount, workThreadCount, 
				isSyncInvokeDidReceivedMsg,
				hostRedirectTo, portRedirectTo);
	}
	
	public void startServer(
			int maxConnection, int ioThreadCount, int workThreadCount, 
			boolean isSyncInvokeDidReceivedMsg,
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
			
			boolean isAllocateDirect = false;
			
			//Tcp Client -------------------------
			final TcpClientConfig tcpClientConfig = new TcpClientConfig();
			tcpClientConfig.setHost(hostRedirectTo);
			tcpClientConfig.setPort(portRedirectTo);
			tcpClientConfig.setConnectTimeoutMS(500);
			tcpClientConfig.setSoTimeoutMS(1000);
			tcpClientConfig.setReceiveBufferSize(SocketReceiveBufferSize);
			tcpClientConfig.setSendBufferSize(SocketReceiveBufferSize);

			//tcp client
			int maxTcpClientPool = workThreadCount;
			GenericObjectPoolConfig tcpClientPoolConfig = new GenericObjectPoolConfig();
			tcpClientPoolConfig.setMaxTotal(maxTcpClientPool);
			tcpClientPoolConfig.setMaxIdle(maxTcpClientPool);
			tcpClientPoolConfig.setMaxWaitMillis(100);

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
						public AbstractTcpEventHandler createHandler(int sessionId,
								//SelectionKey readKey, 
								SelectionKey writeKey) {
							return (new TcpServerEventHandlerBySyncClient(
									tcpClientConfig, sessionId, 
									//readKey, 
									writeKey));
						}
					},
					isSyncInvokeDidReceivedMsg
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
	
	private class TcpServerEventHandlerBySyncClient extends AbstractTcpEventHandler {

		//private SyncTcpClient tcpClient;
		private PooledSyncTcpClient tcpClient;
		
		public TcpServerEventHandlerBySyncClient(				
				TcpClientConfig tcpConfig,
				int sessionId,
				//SelectionKey readKey,
				SelectionKey writeKey) {
			super(sessionId, 
					//readKey, 
					writeKey);
			
			//tcpClient = new SyncTcpClient(tcpConfig);
			tcpClient = _tcpClientPool.borrowObject();
			
//			try {
//				tcpClient.connect();
//			} catch(Throwable e) {
//				e.printStackTrace();
//			}
		}
		
		@Override
		public void didConnect() {
			// TODO Auto-generated method stub
			
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
		public void didReceivedMsg(MessageList<? extends ByteBuff> messages) {
			Iterator<? extends ByteBuff> iter = messages.iterator();
			ByteBuff msg;
			while(iter.hasNext()) {
				msg = iter.next();

				didReceivedMsg(msg);
			}
		}

		@Override
		public void didReceivedMsg(ByteBuff msg) {
			try {
				msg.getByteBuffer().flip();
				tcpClient.send(msg.getByteBuffer().array(), 
						msg.getByteBuffer().position(), msg.getByteBuffer().remaining());
				
				
				try {
					msg.getByteBuffer().clear();
					int receiveLen = tcpClient.receive(
							msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
					if(receiveLen > 0) {
						if(msg.getByteBuffer().array()[0] == '\r') {
							logger.error("didReceivedMsg() reply starts with '\\r'");
						}
						msg.getByteBuffer().position(0);
						msg.getByteBuffer().limit(receiveLen);
						writeMessage(msg.getByteBuffer());
					}
				} catch(SocketTimeoutException e) {
					logger.error("Tcp Client receive timeout---");
				}
				
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
		
	}
	
	private class TcpServerEventHandlerByAsyncClient extends AbstractTcpEventHandler {
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
					new ITcpEventHandlerFactory() {
						
						@Override
						public AbstractTcpEventHandler createHandler(int sessionId,
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
		public void didReceivedMsg(MessageList<? extends ByteBuff> messages) {
			Iterator<? extends ByteBuff> iter = messages.iterator();
			ByteBuff msg;
			while(iter.hasNext()) {
				msg = iter.next();

				didReceivedMsg(msg);
			}
		}

		@Override
		public void didReceivedMsg(ByteBuff msg) {
			try {
				msg.getByteBuffer().flip();
				tcpClient.send(msg.getByteBuffer());
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
	
		
		private class ClientEventHandler extends AbstractTcpEventHandler {

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
			public void didReceivedMsg(MessageList<? extends ByteBuff> messages) {
				Iterator<? extends ByteBuff> iter = messages.iterator();
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
	
	/*
	private class TcpServerEventHandlerPool {
		private ConcurrentHashMap<Integer, TcpServerEventHandler> _handlerMap = new ConcurrentHashMap<Integer, TestTcpServer.TcpServerEventHandler>();
		
		public TcpServerEventHandlerPool() {
		}
		
		public TcpServerEventHandler getHandler(SelectionKeyWrapper keyWrapper) {
			TcpServerEventHandler handler = _handlerMap.get(keyWrapper.getId());
			if(handler == null) {
				handler = new TcpServerEventHandler();
				_handlerMap.put(keyWrapper.getId(), handler);
			}
			
			return handler;
		}
	}
	
	private class TcpServerEventHandler extends AbstractTcpEventHandler {
		private PooledSyncTcpClient tcpClient;
		
		public TcpServerEventHandler() {
			super();

			tcpClient = _tcpClientPool.borrowObject();
		} 

		@Override
		public void didConnect(SelectionKeyWrapper keyWrapper) {
		}

		@Override
		public void didDisconnect(SelectionKeyWrapper keyWrapper) {
			if(tcpClient != null) {
				try {
					tcpClient.returnToPool();
					logger.debug("tcpClient returnToPool:" + keyWrapper.getId());
				} catch(Throwable e) {
					logger.error(null, e);
				}
			}
		}

		@Override
		public void didReceivedMsg(SelectionKeyWrapper keyWrapper, MessageList<? extends ByteBuff> messages) {
			Iterator<? extends ByteBuff> iter = messages.iterator();
			ByteBuff msg;
			while(iter.hasNext()) {
				msg = iter.next();

				didReceivedMsg(keyWrapper, msg);
			}
		}

		@Override
		public void didReceivedMsg(SelectionKeyWrapper keyWrapper, ByteBuff msg) {
			
			try {

				msg.getByteBuffer().flip();
				
				
				tcpClient.send(msg.getByteBuffer().array(), msg.getByteBuffer().position(), msg.getByteBuffer().limit());
				
				msg.getByteBuffer().clear();
				
				try {
					int receiveLen = tcpClient.receive(msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
					if(receiveLen > 0) {
						msg.getByteBuffer().limit(receiveLen);
						writeMessage(keyWrapper, msg.getByteBuffer());
					}
				} catch(SocketTimeoutException e) {
					logger.error("Tcp Client receive timeout---");
				}
			} catch(Throwable e) {
				logger.error(null, e);
			} finally {
			}
		}
	}
	*/
}
