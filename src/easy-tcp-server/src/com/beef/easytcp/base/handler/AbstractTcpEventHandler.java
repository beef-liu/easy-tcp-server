package com.beef.easytcp.base.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.server.TcpException;

public abstract class AbstractTcpEventHandler {
	protected int _sessionId;
	//protected SelectionKey _readKey;
	protected SelectionKey _writeKey;
	
	public AbstractTcpEventHandler(int sessionId,
			//SelectionKey readKey,
			SelectionKey writeKey) {
		_sessionId = sessionId;
		//_readKey = readKey;
		_writeKey = writeKey;
	}
	
//	public final SelectionKey getReadKey() {
//		return _readKey;
//	}

	public final SelectionKey getWriteKey() {
		return _writeKey;
	}
	
	public void destroy() {
		//_readKey = null;
		_writeKey = null;
	}
	
	public abstract void didConnect();
	
	public abstract void didDisconnect();
	
	public abstract void didReceivedMsg(MessageList<? extends ByteBuff> messages);
	
	public abstract void didReceivedMsg(ByteBuff message);
	
	/**
	 * write msg(response) through socketChannel
	 * @param msg
	 * @return
	 * @throws TcpException
	 */
	public int writeMessage(ByteBuffer msg) throws TcpException {
		try {
			_writeKey.selector().select(100);
		} catch (Throwable e) {
			throw new TcpException(e);
			//return 0;
		}
		
		if(!_writeKey.isValid()) {
			throw new TcpException("SelectionKey is not valid");
		}

		try {
			if(_writeKey.isWritable()) {
				SocketChannel socketChannel = (SocketChannel) _writeKey.channel();
				
				if(!SocketChannelUtil.isConnected(socketChannel.socket())) {
					if(SocketChannelUtil.clearSelectionKey(_writeKey)) {
						didDisconnect();
					}
					
					return 0;
				} else {
					return socketChannel.write(msg);
				}
			} else {
				return 0;
			}
		} catch(CancelledKeyException e) {
			if(SocketChannelUtil.clearSelectionKey(_writeKey)) {
				didDisconnect();
			}
			throw new TcpException(e);
		} catch (IOException e) {
			throw new TcpException(e);
		}
		
	}
	
	
}
