package com.beef.easytcp.base.handler;

import java.nio.channels.SelectionKey;


public interface ITcpEventHandlerFactory {
	
	public AbstractTcpEventHandler createHandler(int sessionId,
			//SelectionKey readKey,
			SelectionKey writeKey);
		
}
