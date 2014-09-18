package com.beef.easytcp.server.base;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChannelByteBuffer {
	private ReentrantLock _readBufferLock = new ReentrantLock();
	private ReentrantLock _writeBufferLock = new ReentrantLock();
	
	private volatile boolean _isReadLocked = false;
	private volatile boolean _isWriteLocked = false;
	
	
	private ByteBuffer _readBuffer;
	private ByteBuffer _writeBuffer;
	
	public ChannelByteBuffer(ByteBuffer readBuffer, ByteBuffer writeBuffer) {
		_readBuffer = readBuffer;
		_writeBuffer = writeBuffer;
		
		_readBuffer.clear();
		_writeBuffer.clear();
	}
	
	public ChannelByteBuffer(boolean isAllocateDirect, int readBufferByteSize, int writeBufferByteSize) {
		if(isAllocateDirect) {
			_readBuffer = ByteBuffer.allocateDirect(readBufferByteSize);
			_writeBuffer = ByteBuffer.allocateDirect(writeBufferByteSize);;
		} else {
			_readBuffer = ByteBuffer.allocate(readBufferByteSize);
			_writeBuffer = ByteBuffer.allocate(writeBufferByteSize);;
		}
		
		_readBuffer.clear();
		_writeBuffer.clear();
	}

	public ByteBuffer getReadBuffer() {
		return _readBuffer;
	}

	public ByteBuffer getWriteBuffer() {
		return _writeBuffer;
	}

	/*
	public ReentrantLock getReadBufferLock() {
		return _readBufferLock;
	}

	public ReentrantLock getWriteBufferLock() {
		return _writeBufferLock;
	}
	*/
	
	public boolean tryLockReadBuffer() {
		if(_isReadLocked) {
			return false;
		} else {
			_isReadLocked = false;
			return true;
		}
	}

	public void unlockReadBufferLock() {
		_isReadLocked = false;
	}
	
	public boolean tryLockWriteBuffer() {
		if(_isWriteLocked) {
			return false;
		} else {
			_isWriteLocked = false;
			return true;
		}
	}
	
	public void unlockWriteBufferLock() {
		_isWriteLocked = false;
	}
}
