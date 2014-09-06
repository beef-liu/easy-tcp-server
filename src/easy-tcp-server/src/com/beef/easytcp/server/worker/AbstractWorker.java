package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

public abstract class AbstractWorker implements IWorker {
	private final static Logger logger = Logger.getLogger(AbstractWorker.class);
	
	protected ConcurrentLinkedQueue<SelectionKey> _didReadRequestQueue = 
			new ConcurrentLinkedQueue<SelectionKey>();

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
				logger.error(null, e);
			}
		}
	}
	
	protected abstract void handleDidReadRequest(final SelectionKey key);
	
}
