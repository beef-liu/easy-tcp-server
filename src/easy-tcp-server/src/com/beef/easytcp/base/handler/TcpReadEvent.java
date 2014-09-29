package com.beef.easytcp.base.handler;

import java.util.Iterator;

import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.thread.ITask;

public class TcpReadEvent implements ITask {
	private final static Logger logger = Logger.getLogger(TcpReadEvent.class);

	protected int _sessionId;
	protected ITcpReadEventHandler _eventHandler;
	protected ITcpReplyMessageHandler _replyMessageHandler;
	protected MessageList<IByteBuff> _msgs;
	
	public TcpReadEvent(
			int sessionId,
			ITcpReadEventHandler eventHandler,
			ITcpReplyMessageHandler replyMessageHandler,
			MessageList<IByteBuff> msgs
			) {
		_sessionId = sessionId;
		_eventHandler = eventHandler;
		_replyMessageHandler = replyMessageHandler;
		_msgs = msgs;
	}
	
	@Override
	public void run() {
		try {
			_eventHandler.didReceiveMessage(_replyMessageHandler, _msgs);
		} catch(Throwable e) {
			logger.error(null, e);
		}
	}

	@Override
	public int getTaskGroupId() {
		return _sessionId;
	}

	@Override
	public void destroy() {
		try {
			Iterator<IByteBuff> iterMsgs = _msgs.iterator();
			while(iterMsgs.hasNext()) {
				try {
					iterMsgs.next().destroy();
				} catch(Throwable e) {
					logger.error(null, e);
				}
			}
			_msgs.clear();
		} catch(Throwable e) {
			logger.error(null, e);
		}
		
		_sessionId = 0;
		_eventHandler = null;
		_replyMessageHandler = null;
		_msgs = null;
	}

}
