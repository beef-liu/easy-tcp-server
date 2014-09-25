package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;

public interface AbstractWorkerDispatcher extends Runnable {
	
	public void destroy();
	
	public void addDidReadRequest(SelectionKey key);
}
