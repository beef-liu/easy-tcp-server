package com.beef.easytcp.server.junittest;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.beef.easytcp.client.SyncTcpClient;
import com.beef.easytcp.server.TcpServer;
import com.beef.easytcp.server.base.ChannelByteBuffer;
import com.beef.easytcp.server.config.TcpServerConfig;
import com.beef.easytcp.server.worker.AbstractWorker;
import com.beef.easytcp.server.worker.AbstractWorkerDispatcher;
import com.beef.easytcp.server.worker.IWorker;
import com.beef.easytcp.server.worker.IWorkerFactory;

public class TcpServerTest {
	
	public static void main(String[] args) {
		try {
			TcpServerConfig serverConfig = new TcpServerConfig();
			serverConfig.setHost("127.0.0.1");
			serverConfig.setPort(6381);
			serverConfig.setConnectMaxCount(128);
			serverConfig.setConnectTimeout(5000);
			serverConfig.setConnectWaitCount(16);
			serverConfig.setSocketIOThreadCount(2);
			serverConfig.setSocketReceiveBufferSize(1024*16);
			serverConfig.setSocketSendBufferSize(1024*16);
			
			TcpServer server = new TcpServer(serverConfig, 
					new WorkerDispatcher(new WorkerFactory(), 1));
			
			server.start();
			System.out.println("Start server -------------");
			
			Thread.sleep(600000);
			
			System.out.println("Shuting down server -------------");
			server.shutdown();
			System.out.println("Shutted down server -------------");
		} catch(Throwable e) {
			e.printStackTrace();
		}
		
	}
	
	protected static class WorkerFactory implements IWorkerFactory {

		@Override
		public IWorker createWorker() {
			return new Worker("127.0.0.1", 6379);
		}
		
	}

	protected static class WorkerDispatcher extends AbstractWorkerDispatcher {

		public WorkerDispatcher(IWorkerFactory workerFactory, int workerCount) {
			super(workerFactory, workerCount);
		}

		@Override
		protected int chooseWorkerToDispatch(SelectionKey key) {
			return 0;
		}
	}
	
	protected static class Worker extends AbstractWorker {
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
		public void shutdown() {
			try {
				_tcpClient.disconnect();
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void handleDidReadRequest(SelectionKey key) {
			ChannelByteBuffer buffer = (ChannelByteBuffer) key.attachment();

			buffer.getReadBufferLock().lock();
			try {
				_tcpClient.send(buffer.getReadBuffer().array(), 0, buffer.getReadBuffer().position());
				buffer.getReadBuffer().clear();
			} catch(Throwable e) {
				e.printStackTrace();
			} finally {
				buffer.getReadBufferLock().unlock();
			}
			
			buffer.getWriteBufferLock().lock();
			try {
				int receiveLen = _tcpClient.receive(buffer.getWriteBuffer().array(), 
						buffer.getWriteBuffer().limit(), 
						buffer.getWriteBuffer().capacity() - buffer.getWriteBuffer().limit());
				if(receiveLen > 0) {
					buffer.getWriteBuffer().limit(buffer.getWriteBuffer().limit() + receiveLen);
					outputByteBufferStatus("after write response", buffer.getWriteBuffer());
				}
			} catch(Throwable e) {
				e.printStackTrace();
			} finally {
				buffer.getWriteBufferLock().unlock();
			}

		}
	}

	protected static void outputByteBufferStatus(String msgPrefix, ByteBuffer byteBuff) {
		System.out.println(msgPrefix + " bytebuffer position:" + byteBuff.position() 
				+ " limit:" + byteBuff.limit() 
				+ " remaining:" + byteBuff.remaining());
	}
	
}
