package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

/**
 * This class is likely a demonstration that a way to handle request data to dispatch it to worker thread.
 * You could choose not to use this class and implement your own IWorkerDispatcher
 * @author XingGu Liu
 *
 */
public abstract class AbstractWorkerDispatcher implements IWorkerDispatcher {
	private final static Logger logger = Logger.getLogger(AbstractWorkerDispatcher.class);
	
	protected ConcurrentLinkedQueue<SelectionKey> _didReadRequestQueue = 
			new ConcurrentLinkedQueue<SelectionKey>();
		
	protected IWorkerFactory _workerFactory;
	protected int _workerMaxCount;
	
	protected List<IWorker> _workerList;
	protected ScheduledExecutorService _workerThreadPool;
	
	public AbstractWorkerDispatcher(IWorkerFactory workerFactory, 
			int workerMaxCount, int workerInitCount) {
		_workerFactory = workerFactory;
		_workerMaxCount = workerMaxCount;
		
		_workerList = new ArrayList<IWorker>();
		_workerThreadPool = Executors.newScheduledThreadPool(workerMaxCount);

		long threadPeriod = 1;
		long initialDelay = 500;
		
		for(int i = 0; i < workerInitCount; i++) {
			_workerThreadPool.scheduleAtFixedRate(
					workerFactory.createWorker(), initialDelay, threadPeriod, TimeUnit.MILLISECONDS);
		}
	}
	
	@Override
	public void shutdown() {
		_workerThreadPool.shutdownNow();
	}
	
	@Override
	public void addDidReadRequest(SelectionKey key) {
		_didReadRequestQueue.add(key);
	}
	
	@Override
	public void run() {
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
	}
	
	protected abstract int chooseWorkerToDispatch(SelectionKey key);
	
}
