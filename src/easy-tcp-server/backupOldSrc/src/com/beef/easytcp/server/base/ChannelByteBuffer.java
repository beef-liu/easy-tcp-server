package com.beef.easytcp.server.base;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChannelByteBuffer {
	private ByteBuffer _readBuffer;
	private ByteBuffer _writeBuffer;
	

	private ReentrantLock _readBufferLock = new ReentrantLock();
	private ReentrantLock _writeBufferLock = new ReentrantLock();
	
	public boolean isLockedReadBuffer() {
		return _readBufferLock.isLocked();
	}
	
	public boolean tryLockReadBuffer() {
		return _readBufferLock.tryLock();
	}

	public void unlockReadBufferLock() {
		_readBufferLock.unlock();
	}
	
	public boolean isLockedWriteBuffer() {
		return _writeBufferLock.isLocked();
	}
	
	public boolean tryLockWriteBuffer() {
		return _writeBufferLock.tryLock();
	}
	
	public void unlockWriteBufferLock() {
		_writeBufferLock.lock();
	}
	/*
	private volatile boolean _isReadLocked = false;
	private volatile boolean _isWriteLocked = false;
	
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
	*/
	
	
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
}
