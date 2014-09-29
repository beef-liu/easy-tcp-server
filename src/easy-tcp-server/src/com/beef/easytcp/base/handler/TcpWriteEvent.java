package com.beef.easytcp.base.handler;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.base.thread.ITask;

public class TcpWriteEvent implements ITask {
	private final static Logger logger = Logger.getLogger(TcpWriteEvent.class);

	protected int _sessionId;
	protected IByteBuff _msg;
	protected SelectionKey _writeKey;

	
	public int getSessionId() {
		return _sessionId;
	}

	public SelectionKey getWriteKey() {
		return _writeKey;
	}

	public TcpWriteEvent(
			int sessionId,
			SelectionKey writeKey,
			IByteBuff msg
			) {
		_sessionId = sessionId;
		_writeKey = writeKey;
		_msg = msg;
	}
	
	@Override
	public void run() {
		try {
			SocketChannel socketChannel = (SocketChannel) _writeKey.channel();
			
			if(!SocketChannelUtil.isConnected(socketChannel.socket())) {
				SocketChannelUtil.clearSelectionKey(_writeKey);
				return;
			} else {
				//TODO DEBUG 
				if(_msg.getByteBuffer().array()[_msg.getByteBuffer().position()] == '\r') {
					System.out.println("writeMessage() reply starts with '\\r'. position:" 
							+ _msg.getByteBuffer().position());
				}
				while(_msg.getByteBuffer().hasRemaining()) {
					socketChannel.write(_msg.getByteBuffer());
				}
			}
		} catch(CancelledKeyException e) {
			SocketChannelUtil.clearSelectionKey(_writeKey);
			logger.error(null, e);
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
			_msg.destroy();
		} catch(Throwable e) {
			logger.error(null, e);
		}
		
		_sessionId = 0;
		_writeKey = null;
		_msg = null;
	}

}
