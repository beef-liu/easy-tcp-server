package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.beef.easytcp.server.base.ChannelByteBuffer;

/**
 * This class is likely a demonstration that a way to handle request data to dispatch it to worker thread.
 * You could choose not to use this class and implement your own IWorkerDispatcher
 * @author XingGu Liu
 *
 */
public abstract class DefaultWorkerDispatcher implements AbstractWorkerDispatcher {
	private final static Logger logger = Logger.getLogger(DefaultWorkerDispatcher.class);
	
	protected ConcurrentLinkedQueue<SelectionKey> _didReadRequestQueue = 
			new ConcurrentLinkedQueue<SelectionKey>();
		
	protected IWorkerFactory _workerFactory;
	protected int _workerCount;
	
	protected List<AbstractWorker> _workerList;
	protected ExecutorService _workerThreadPool;
	
	public DefaultWorkerDispatcher(IWorkerFactory workerFactory, 
			int workerCount) {
		_workerFactory = workerFactory;
		_workerCount = workerCount;
		
		_workerList = new ArrayList<AbstractWorker>();
		_workerThreadPool = Executors.newFixedThreadPool(_workerCount);

		long threadPeriod = 1;
		long initialDelay = 500;
		
		for(int i = 0; i < _workerCount; i++) {
			final AbstractWorker worker = workerFactory.createWorker();
			_workerList.add(worker);
//			_workerThreadPool.scheduleAtFixedRate(
//					worker, initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
			_workerThreadPool.execute(worker);
		}
	}
	
	@Override
	public void destroy() {
		_workerThreadPool.shutdownNow();
		
		for(int i = 0; i < _workerList.size(); i++) {
			try {
				_workerList.get(i).destroy();
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
	}
	
	@Override
	public void addDidReadRequest(SelectionKey key) {
		_didReadRequestQueue.add(key);
	}
	
	@Override
	public void run() {
		//logger.debug("WorkerDispatcher >>>>>>>>>>>>>>>>>>>>>>>>");
		while(true) {
			try {
				while(true) {
					final SelectionKey key = _didReadRequestQueue.poll();
					
					if(key == null) {
						break;
					}
					
					if(key.attachment() == null) {
						continue;
					}
					
					try {
						handleDidReadRequest(key);
					} catch(Throwable e) {
						logger.error("WorkerDispatcherThread error", e);
					}
				}
				
			} catch(Throwable e) {
				logger.error("AbstractWorkerDispatcher.run() Error Occurred", e); 
			} finally {
				try {
					Thread.sleep(10);
				} catch(InterruptedException e) {
					logger.info("AbstractWorkerDispatcher InterruptedException -----");
					break;
				}
			}
		}
		//logger.debug("WorkerDispatcher <<<<<<<<<<<<<<<<<<<<<<<<<<<<");
	}
	
	/**
	 * You could override this method to do your business.
	 * Request data has been read into ChannelByteBuffer.
	 * You maybe need to decide dispatch request data to worker thread depend on 
	 * what kind of data in ChannelByteBuffer.getReadBuffer().
	 * Don't forget ChannelByteBuffer.getReadBuffer().position(0) after read ChannelByteBuffer.getReadBuffer(),
	 * The next step in worker thread should need to read data again.
	 * @param key
	 */
	protected void handleDidReadRequest(SelectionKey key) {
		int workerIndex = chooseWorkerToDispatch(key);
		_workerList.get(workerIndex).addDidReadRequest(key);
		logger.debug("dispatch to worker[" + workerIndex + "]");
	}
	
	protected abstract int chooseWorkerToDispatch(SelectionKey key);
	
}
