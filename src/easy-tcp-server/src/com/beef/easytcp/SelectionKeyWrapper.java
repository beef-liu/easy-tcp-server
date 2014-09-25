package com.beef.easytcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.beef.easytcp.server.TcpException;

public class SelectionKeyWrapper {
	private SelectionKey _key;
	
	public SelectionKeyWrapper(SelectionKey key) {
		_key = key;
	}
	
//	public SelectionKey selectionKey() {
//		return _key;
//	}
	
	public SessionObj getSelectionKeyAttach() {
		return (SessionObj) _key.attachment();
	}
		
	public int writeMessage(ByteBuffer msg, ITcpEventHandler eventHandler) throws TcpException {
		
		try {
			_key.selector().select(100);
		} catch (IOException e) {
			throw new TcpException(e);
		}
		
		if(!_key.isValid()) {
			throw new TcpException("SelectionKey is not valid");
		}

		try {
			if(_key.isWritable()) {
				SocketChannel socketChannel = (SocketChannel) _key.channel();
				
				if(!SocketChannelUtil.isConnected(socketChannel.socket())) {
					if(SocketChannelUtil.clearSelectionKey(_key)) {
						if(eventHandler != null) {
							eventHandler.didDisconnect(this);
						}
					}
					
					return 0;
				} else {
					return socketChannel.write(msg);
				}
			} else {
				return 0;
			}
		} catch(CancelledKeyException e) {
			if(SocketChannelUtil.clearSelectionKey(_key)) {
				if(eventHandler != null) {
					eventHandler.didDisconnect(this);
				}
			}
			throw new TcpException(e);
		} catch (IOException e) {
			throw new TcpException(e);
		}
	}
	
}
