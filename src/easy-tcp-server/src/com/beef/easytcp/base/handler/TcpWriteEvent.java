package com.beef.easytcp.base.handler;

import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.base.thread.ITask;

public class TcpWriteEvent implements ITask {
	private final static Logger logger = Logger.getLogger(TcpWriteEvent.class);

	protected int _sessionId;
	protected SelectionKey _writeKey;
	protected MessageList<? extends IByteBuff> _msgs = null;
	protected IByteBuff _msg = null;

	
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

	public TcpWriteEvent(
			int sessionId,
			SelectionKey writeKey,
			MessageList<? extends IByteBuff> msgs
			) {
		_sessionId = sessionId;
		_writeKey = writeKey;
		_msgs = msgs;
	}
	
	@Override
	public void run() {
		try {
			SocketChannel socketChannel = (SocketChannel) _writeKey.channel();
			
			if(!SocketChannelUtil.isConnected(socketChannel.socket())) {
				SocketChannelUtil.clearSelectionKey(_writeKey);
				return;
			} else {
				if(_msg != null) {
					//TODO DEBUG 
					if(_msg.getByteBuffer().array()[0] == '\r') {
						System.out.println("writeMessage() reply starts with '\\r'.");
					}

					while(_msg.getByteBuffer().hasRemaining()) {
						socketChannel.write(_msg.getByteBuffer());
					}
				} else {
					ByteBuffer[] bufferArray = new ByteBuffer[_msgs.size()];
					
					Iterator<? extends IByteBuff> iterMsgs = _msgs.iterator();
					int index = 0;
					while(iterMsgs.hasNext()) {
						try {
							bufferArray[index++] = iterMsgs.next().getByteBuffer(); 
						} catch(Throwable e) {
							logger.error(null, e);
						}
					}
					
					//TODO DEBUG 
					if(bufferArray[0].array()[0] == '\r') {
						System.out.println("writeMessage() replys starts with '\\r'.");
					}
					while(bufferArray[bufferArray.length - 1].hasRemaining()) {
						socketChannel.write(bufferArray);
					}
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
		if(_msg != null) {
			try {
				_msg.destroy();
			} catch(Throwable e) {
				logger.error(null, e);
			}
		} else {
			Iterator<? extends IByteBuff> iterMsgs = _msgs.iterator();
			while(iterMsgs.hasNext()) {
				try {
					iterMsgs.next().destroy();
				} catch(Throwable e) {
					logger.error(null, e);
				}
			}
			_msgs.clear();
		}
		
		_sessionId = 0;
		_writeKey = null;
		_msg = null;
	}

}
