package com.beef.easytcp.server.worker;

import java.nio.channels.SelectionKey;

public abstract class AbstractWorker extends Thread {
	public abstract void addDidReadRequest(SelectionKey key);
	
	public abstract void destroy();
}
