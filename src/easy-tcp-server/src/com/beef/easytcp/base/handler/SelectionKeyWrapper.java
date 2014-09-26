package com.beef.easytcp.base.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.server.TcpException;

public class SelectionKeyWrapper extends SessionObj {
	private SelectionKey _readKey;
	private SelectionKey _writeKey;
	//private SessionObj _sessionObj;
	
	public SelectionKeyWrapper(int sessionId,
			SelectionKey readKey,
			SelectionKey writeKey) {
		super(sessionId);
		
		_readKey = readKey;
		_writeKey = writeKey;
	}
	

	
//	public SelectionKey selectionKey() {
//		return _key;
//	}
	
	public SelectionKey getReadKey() {
		return _readKey;
	}

	public SelectionKey getWriteKey() {
		return _writeKey;
	}

	public void destroy() {
		_writeKey = null;
		_userObj = null;
		_id = 0;
	}
	
}
