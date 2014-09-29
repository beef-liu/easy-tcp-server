package com.beef.easytcp.base.handler;

import java.nio.channels.SelectionKey;

public class TcpSession {
	protected int _sessionId;
	protected SelectionKey _writeKey;
	protected ITcpEventHandler _eventHandler;

	public int getSessionId() {
		return _sessionId;
	}

	public SelectionKey getWriteKey() {
		return _writeKey;
	}

	public TcpSession(
			int sessionId,
			SelectionKey writeKey,
			ITcpEventHandler eventHandler) {
		_sessionId = sessionId;
		_writeKey = writeKey;
		_eventHandler = eventHandler;
	}

	public void destroy() {
		_sessionId = 0;
		_writeKey = null;
		_eventHandler = null;
	}
}
