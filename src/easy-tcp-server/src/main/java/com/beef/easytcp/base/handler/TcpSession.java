package com.beef.easytcp.base.handler;

import java.nio.channels.SelectionKey;

public class TcpSession {
	protected int _sessionId;
	protected SelectionKey _writeKey;
	protected ITcpEventHandler _eventHandler;
	protected ITcpReplyMessageHandler _replyMsgHandler;

	public int getSessionId() {
		return _sessionId;
	}

	public SelectionKey getWriteKey() {
		return _writeKey;
	}

	public ITcpEventHandler getEventHandler() {
		return _eventHandler;
	}
	
	public ITcpReplyMessageHandler getReplyMsgHandler() {
		return _replyMsgHandler;
	}

	public TcpSession(
			int sessionId,
			SelectionKey writeKey,
			ITcpEventHandler eventHandler,
			ITcpReplyMessageHandler replyMsgHandler
			) {
		_sessionId = sessionId;
		_writeKey = writeKey;
		_eventHandler = eventHandler;
		_replyMsgHandler = replyMsgHandler;
	}

	public void destroy() {
		_sessionId = 0;
		_writeKey = null;
		_eventHandler = null;
		_replyMsgHandler = null;
	}

}
