package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;

public interface IWorker extends Runnable {
	public void addDidReadRequest(SelectionKey key);
	
	public void shutdown();
}
