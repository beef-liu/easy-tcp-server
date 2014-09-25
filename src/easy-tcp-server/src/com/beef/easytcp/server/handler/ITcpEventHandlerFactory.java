package com.beef.easytcp.server.handler;

import com.beef.easytcp.ITcpEventHandler;
import com.beef.easytcp.SessionObj;

public interface ITcpEventHandlerFactory {
	
	public ITcpEventHandler createHandler(SessionObj sessionObj);
	
}
