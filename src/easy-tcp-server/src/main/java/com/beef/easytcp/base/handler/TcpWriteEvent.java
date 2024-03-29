package com.beef.easytcp.base.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.SocketChannelUtil;
import com.beef.easytcp.util.thread.ITask;

public class TcpWriteEvent implements ITask {
	private final static Log logger = LogFactory.getLog(TcpWriteEvent.class);

	protected int _sessionId;
	protected SelectionKey _writeKey;
	
	//send type ---> msg list
	protected MessageList<? extends IByteBuff> _msgs = null;
	//send type ---> msg
	protected IByteBuff _msg = null;
	
	//send type ---> file channel
	protected FileChannel _fileChannel = null;
	protected long _position;
	protected long _byteLen;
	
	//send type ---> file
	protected File _file = null;
	
	//send type ---> ByteBuffer
	protected ByteBuffer _byteBuffer;
	
	
	
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
	
	public TcpWriteEvent(
			int sessionId,
			SelectionKey writeKey,
			FileChannel fileChannel, long position, long byteLen
			) {
		_sessionId = sessionId;
		_writeKey = writeKey;
		
		_fileChannel = fileChannel;
		_position = position;
		_byteLen = byteLen;
	}
	
	public TcpWriteEvent(
			int sessionId,
			SelectionKey writeKey,
			File file) {
		_sessionId = sessionId;
		_writeKey = writeKey;

		_file = file;
	}
	
	public TcpWriteEvent(
			int sessionId,
			SelectionKey writeKey,
			ByteBuffer byteBuffer) {
		_sessionId = sessionId;
		_writeKey = writeKey;

		_byteBuffer = byteBuffer;
	}
	
	@Override
	public void run() {
		try {
			if(_writeKey == null) {
				logger.debug("TcpWriteEvent() _writeKey is null");
			} else {
				if(_writeKey.selector() == null) {
					logger.debug("TcpWriteEvent() _writeKey.selector() is null");
				}
			}
			_writeKey.selector().select();
			
			SocketChannel socketChannel = (SocketChannel) _writeKey.channel();
			
			if(!SocketChannelUtil.isConnected(socketChannel.socket())) {
				//logger.debug("TcpWriteEvent close ----------");
				SocketChannelUtil.clearSelectionKey(_writeKey);
				return;
			} else {
				//logger.debug("TcpWriteEvent write ----------");
				if(_msg != null) {
					//TODO DEBUG 
//					if(_msg.getByteBuffer().array()[0] == '\r') {
//						System.out.println("writeMessage() reply starts with '\\r'.");
//					}

					//to prevent from doing loop forever
					final int iterMax = Math.max(64, 16 * (_msg.getByteBuffer().remaining() / socketChannel.socket().getSendBufferSize()));
					int iterCnt = 0;
					while(_msg.getByteBuffer().hasRemaining() && (iterCnt++) < iterMax) {
						socketChannel.write(_msg.getByteBuffer());
					}
					if(iterCnt >= iterMax) {
						logger.warn("TcpWriteEvent retry too many times. iterCnt:" + iterCnt);
					}
				} else if(_msgs != null) {
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
//					if(bufferArray[0].array()[0] == '\r') {
//						System.out.println("writeMessage() replys starts with '\\r'.");
//					}
					//to prevent from doing loop forever
					final int iterMax = bufferArray.length * Math.max(64, 16 * (bufferArray[bufferArray.length - 1].remaining() / socketChannel.socket().getSendBufferSize()));
					int iterCnt = 0;
					while(bufferArray[bufferArray.length - 1].hasRemaining() && (iterCnt++) < iterMax) {
						socketChannel.write(bufferArray);
					}
					if(iterCnt >= iterMax) {
						logger.warn("TcpWriteEvent retry too many times. iterCnt:" + iterCnt);
					}
				} else if(_fileChannel != null) {
					//_fileChannel.transferTo(_position, _byteLen, socketChannel);
					sendFromFileChannel(socketChannel, _fileChannel, _position, _byteLen);
				} else if(_file != null) {
					FileInputStream fis = new FileInputStream(_file);
					try {
						//fis.getChannel().transferTo(0, _file.length(), socketChannel);
						sendFromFileChannel(socketChannel, fis.getChannel(), 0, _file.length());
					} finally {
						fis.close();
					}
				} else if (_byteBuffer != null) {
					//to prevent from doing loop forever
					final int iterMax = Math.max(64, 16 * (_byteBuffer.remaining() / socketChannel.socket().getSendBufferSize()));
					int iterCnt = 0;
					while(_byteBuffer.hasRemaining() && (iterCnt++) < iterMax) {
						socketChannel.write(_byteBuffer);
					}
					if(iterCnt >= iterMax) {
						logger.warn("TcpWriteEvent retry too many times. iterCnt:" + iterCnt);
					}
				} else {
					logger.error("Unknown sending type.");
				}
			}
		} catch(CancelledKeyException e) {
			SocketChannelUtil.clearSelectionKey(_writeKey);
			logger.error(null, e);
		} catch(Throwable e) {
			logger.error(null, e);
		}
	}
	
	private static void sendFromFileChannel(
			SocketChannel socketChannel, 
			FileChannel src, long position, long length) throws IOException {
		long bufferSize = socketChannel.socket().getSendBufferSize();
		
		long offset = position;
		long remainder = length;
		long writeLen;
		//to prevent from doing loop forever
		final long iterMax = Math.max(64, (length / bufferSize) * 16);
		long iterCnt = 0;
		while(remainder > 0 && (iterCnt++) < iterMax) {
			writeLen = Math.min(remainder, bufferSize);
			
//			if( != writeLen) {
//				throw new RuntimeException("transfer failed.");
//			}
			
			writeLen = src.transferTo(offset, writeLen, socketChannel);
			
			remainder -= writeLen;
			offset += writeLen;
		}
		if(iterCnt >= iterMax) {
			logger.warn("TcpWriteEvent retry too many times. iterCnt:" + iterCnt);
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
		} else if(_msgs != null) {
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
