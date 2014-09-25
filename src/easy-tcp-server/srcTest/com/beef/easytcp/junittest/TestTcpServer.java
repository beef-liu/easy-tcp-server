package com.beef.easytcp.junittest;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Iterator;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.ByteBuff;
import com.beef.easytcp.ITcpEventHandler;
import com.beef.easytcp.MessageList;
import com.beef.easytcp.SelectionKeyWrapper;
import com.beef.easytcp.SessionObj;
import com.beef.easytcp.client.AsyncTcpClient;
import com.beef.easytcp.client.SyncTcpClient;
import com.beef.easytcp.client.TcpClientConfig;
import com.beef.easytcp.client.pool.PooledSyncTcpClient;
import com.beef.easytcp.client.pool.SyncTcpClientPool;
import com.beef.easytcp.server.TcpServer;
import com.beef.easytcp.server.buffer.PooledByteBuffer;
import com.beef.easytcp.server.config.TcpServerConfig;
import com.beef.easytcp.server.handler.ITcpEventHandlerFactory;

public class TestTcpServer {

	private final static Logger logger = Logger.getLogger(TestTcpServer.class);
	
	private final static int SocketReceiveBufferSize = 1024 * 4;
	
	public static void main(String[] args) {
		TestTcpServer test = new TestTcpServer();
		test.startServer();
	}
	
	private SyncTcpClientPool _tcpClientPool;
	
	public void startServer() {
		try {
			int maxConnection = (int) (1024 * 1);
			int maxClient = (int) (1024 * 1);
			
			TcpServerConfig serverConfig = new TcpServerConfig();
			serverConfig.setHost("127.0.0.1");
			serverConfig.setPort(6381);
			serverConfig.setConnectMaxCount(maxConnection);
			serverConfig.setConnectTimeout(100);
			serverConfig.setConnectWaitCount(maxConnection);
			serverConfig.setSocketIOThreadCount(2);
			serverConfig.setSocketReceiveBufferSize(SocketReceiveBufferSize);
			serverConfig.setSocketSendBufferSize(SocketReceiveBufferSize);
			
			int workerCount = 8;
			boolean isAllocateDirect = false;
			
			//Tcp Client -------------------------
			TcpClientConfig tcpClientConfig = new TcpClientConfig();
			tcpClientConfig.setHost("127.0.0.1");
			tcpClientConfig.setPort(6379);
			tcpClientConfig.setConnectTimeoutMS(500);
			tcpClientConfig.setSoTimeoutMS(100);
			tcpClientConfig.setReceiveBufferSize(SocketReceiveBufferSize);
			tcpClientConfig.setSendBufferSize(SocketReceiveBufferSize);
			
			GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
			byteBufferPoolConfig.setMaxTotal(maxClient);
			byteBufferPoolConfig.setMaxIdle(maxClient);
			byteBufferPoolConfig.setMaxWaitMillis(100);

			_tcpClientPool = new SyncTcpClientPool(byteBufferPoolConfig, tcpClientConfig);
			
			
			
			final TcpServerEventHandler serverEventHandler = new TcpServerEventHandler();
			TcpServer server = new TcpServer(serverConfig, isAllocateDirect, 
					new ITcpEventHandlerFactory() {
						
						@Override
						public ITcpEventHandler createHandler(SessionObj sessionObj) {
							// TODO Auto-generated method stub
							return serverEventHandler;
						}
					});
			
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
	
	private class TcpServerEventHandler implements ITcpEventHandler {
		
		public TcpServerEventHandler() throws IOException {
		} 

		@Override
		public void didConnect(SelectionKeyWrapper selectionKey) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void didDisconnect(SelectionKeyWrapper selectionKey) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void didReceivedMsg(SelectionKeyWrapper selectionKey,
				MessageList<? extends ByteBuff> messages) {
			Iterator<? extends ByteBuff> iter = messages.iterator();
			ByteBuff msg;
			while(iter.hasNext()) {
				msg = iter.next();
				didReceivedMsg(selectionKey, msg);
			}
		}

		@Override
		public void didReceivedMsg(SelectionKeyWrapper selectionKey,
				ByteBuff msg) {
			PooledSyncTcpClient tcpClient = null;
			try {
				tcpClient = _tcpClientPool.borrowObject();

				msg.getByteBuffer().flip();
				
				
				tcpClient.send(msg.getByteBuffer().array(), msg.getByteBuffer().position(), msg.getByteBuffer().limit());
				
				msg.getByteBuffer().clear();
				
				try {
					int receiveLen = tcpClient.receive(msg.getByteBuffer().array(), 0, msg.getByteBuffer().limit());
					if(receiveLen > 0) {
						msg.getByteBuffer().limit(receiveLen);
						selectionKey.writeMessage(msg.getByteBuffer(), null);
					}
				} catch(SocketTimeoutException e) {
					logger.error("Tcp Client receive timeout---");
				}
			} catch(Throwable e) {
				logger.error(null, e);
			} finally {
				if(tcpClient != null) {
					try {
						tcpClient.returnToPool();
					} catch(Throwable e) {
						logger.error(null, e);
					}
				}
			}
		}
	}
	
}
