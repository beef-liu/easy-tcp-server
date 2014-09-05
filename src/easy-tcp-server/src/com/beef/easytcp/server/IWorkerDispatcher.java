package com.beef.easytcp.server;

import java.nio.channels.SelectionKey;

public interface IWorkerDispatcher {
	public void handleDidRead(SelectionKey key);
	
}
