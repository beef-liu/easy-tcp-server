package com.beef.easytcp.base.handler;

public interface ITcpEventHandlerFactory {
	
	public ITcpEventHandler createHandler(int sessionId);
		
}
