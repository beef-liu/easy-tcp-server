package com.beef.easytcp;

import java.nio.ByteBuffer;

public class ByteBuff {
	protected ByteBuffer _backBuffer;
	
	public ByteBuff(boolean isAllocateDirect, int bufferByteSize) {
		if(isAllocateDirect) {
			_backBuffer = ByteBuffer.allocateDirect(bufferByteSize);
		} else {
			_backBuffer = ByteBuffer.allocate(bufferByteSize);
		}
	}
	
	public ByteBuff(ByteBuffer byteBuff) {
		_backBuffer = byteBuff;
	}
	
	public ByteBuffer getByteBuffer() {
		return _backBuffer;
	}

}
