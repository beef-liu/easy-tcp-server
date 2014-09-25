package com.beef.easytcp.server.junittest;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Random;

import org.apache.log4j.Logger;

import com.beef.easytcp.client.SyncTcpClient;
import com.beef.easytcp.server.TcpServer;
import com.beef.easytcp.server.base.ChannelByteBuffer;
import com.beef.easytcp.server.config.TcpServerConfig;
import com.beef.easytcp.server.worker.DefaultWorker;
import com.beef.easytcp.server.worker.DefaultWorkerDispatcher;
import com.beef.easytcp.server.worker.AbstractWorker;
import com.beef.easytcp.server.worker.IWorkerFactory;

public class TcpServerTest {
	private final static Logger logger = Logger.getLogger(TcpServerTest.class);
	
	public static void main(String[] args) {
		try {
			TcpServerConfig serverConfig = new TcpServerConfig();
			serverConfig.setHost("127.0.0.1");
			serverConfig.setPort(6381);
			serverConfig.setConnectMaxCount(10000);
			serverConfig.setConnectTimeout(5000);
			serverConfig.setConnectWaitCount(1024);
			serverConfig.setSocketIOThreadCount(2);
			serverConfig.setSocketReceiveBufferSize(1024*16);
			serverConfig.setSocketSendBufferSize(1024*16);
			
			int workerCount = 8;
			TcpServer server = new TcpServer(serverConfig, 
					new WorkerDispatcher(new WorkerFactory(), workerCount));
			
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
	
	protected static class WorkerFactory implements IWorkerFactory {

		@Override
		public AbstractWorker createWorker() {
			return new Worker("127.0.0.1", 6379);
		}
		
	}

	protected static class WorkerDispatcher extends DefaultWorkerDispatcher {
		//private Random _rand = new Random();
		long _dispatchCount = 0;
		
		public WorkerDispatcher(IWorkerFactory workerFactory, int workerCount) {
			super(workerFactory, workerCount);
		}

		@Override
		protected int chooseWorkerToDispatch(SelectionKey key) {
			//return _rand.nextInt(_workerCount);
			return (int) (_dispatchCount++) % _workerCount;
			//return 0;
		}
	}
	
	protected static class Worker extends DefaultWorker {
		private SyncTcpClient _tcpClient; 
		
		public Worker(String hostPassTo, int portPassTo) {
			try {
				_tcpClient = new SyncTcpClient(hostPassTo, portPassTo, 5000);
				_tcpClient.connect();
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
		
		@Override
		public void destroy() {
			try {
				_tcpClient.disconnect();
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void handleDidReadRequest(SelectionKey key) {
			ChannelByteBuffer buffer = (ChannelByteBuffer) key.attachment();

			/*
			if(buffer.tryLockReadBuffer()) {
				try {
					_tcpClient.send(buffer.getReadBuffer().array(), 0, buffer.getReadBuffer().position());
					buffer.getReadBuffer().clear();
				} catch(Throwable e) {
					e.printStackTrace();
				} finally {
					buffer.unlockReadBufferLock();
				}
			}
			*/
			try {
				final byte[] buf = buffer.getReadBuffer().array();
				byte rtn = (byte)0x0a;
				int cmdEnd;
				int offset = 0;
				while((cmdEnd = Arrays.binarySearch(buf, rtn)) >= 0) {
					//send one cmd
					_tcpClient.send(
							buf, 0, buffer.getReadBuffer().position());
					
					
				}
				
			} catch(Throwable e) {
				logger.error(null, e);
			}
			
			/*
			if(buffer.tryLockWriteBuffer()) {
				try {
					int receiveLen = _tcpClient.receive(buffer.getWriteBuffer().array(), 
							buffer.getWriteBuffer().limit(), 
							buffer.getWriteBuffer().capacity() - buffer.getWriteBuffer().limit());
					if(receiveLen > 0) {
						buffer.getWriteBuffer().limit(buffer.getWriteBuffer().limit() + receiveLen);
						//outputByteBufferStatus("after write response", buffer.getWriteBuffer());
					}
				} catch(Throwable e) {
					e.printStackTrace();
				} finally {
					buffer.unlockWriteBufferLock();
				}
			}
			*/

			try {
				int receiveLen = _tcpClient.receive(buffer.getWriteBuffer().array(), 
						buffer.getWriteBuffer().limit(), 
						buffer.getWriteBuffer().capacity() - buffer.getWriteBuffer().limit());
				if(receiveLen > 0) {
					buffer.getWriteBuffer().limit(buffer.getWriteBuffer().limit() + receiveLen);
					//outputByteBufferStatus("after write response", buffer.getWriteBuffer());
				}
			} catch(Throwable e) {
				logger.error(null, e);
			}
			
//			try {
//				Thread.sleep(1);
//			} catch(InterruptedException e) {
//			}
		}
	}

	protected static void outputByteBufferStatus(String msgPrefix, ByteBuffer byteBuff) {
		System.out.println(msgPrefix + " bytebuffer position:" + byteBuff.position() 
				+ " limit:" + byteBuff.limit() 
				+ " remaining:" + byteBuff.remaining());
	}
	
}
