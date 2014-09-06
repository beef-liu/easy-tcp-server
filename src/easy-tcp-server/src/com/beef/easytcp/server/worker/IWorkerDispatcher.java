package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;

public interface IWorkerDispatcher extends Runnable {
	
	public void shutdown();
	
	public void addDidReadRequest(SelectionKey key);
}
