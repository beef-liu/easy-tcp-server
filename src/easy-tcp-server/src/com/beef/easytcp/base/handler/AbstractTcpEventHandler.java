package com.beef.easytcp.base.handler;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.beef.easytcp.base.ByteBuff;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.server.TcpException;

public abstract class AbstractTcpEventHandler implements ITcpEventHandler {
	
}

/* old version -------
public abstract class AbstractTcpEventHandler <MsgType extends IByteBuff> {
	protected int _sessionId;
	//protected SelectionKey _readKey;
	protected SelectionKey _writeKey;
	protected Object _lockForWrite = new Object();
	
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
	
	public abstract void didReceiveMessage(MessageList<MsgType> msgs);
	
	public abstract void didReceiveMessage(MsgType msg);
	
	public int sendMessage(ByteBuffer msg) throws TcpException {
//		if(_writeKey == null) {
//			//already destroyed
//			return 0;
//		}
//		
//		if(_writeKey.selector() == null) {
//			//already destroyed
//			return 0;
//		}
		
		try {
			_writeKey.selector().select(100);
		} catch (Throwable e) {
			//mostly destroyed by cause of "Connection reset by peer"
			throw new TcpException("mostly destroyed by cause of \"Connection reset by peer\"\r\n", e);
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
					//synchronized (_lockForWrite) 
					{
						if(msg.array()[msg.position()] == '\r') {
							System.out.println("writeMessage() reply starts with '\\r'. position:" + msg.position());
						}
						int totalWriteLen = msg.remaining();
						while(msg.hasRemaining()) {
							socketChannel.write(msg);
						}
						return totalWriteLen;
					}
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

*/
