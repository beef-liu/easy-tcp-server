package com.beef.easytcp.base;

import java.nio.ByteBuffer;

public class ByteBuff implements IByteBuff {
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
	
	@Override
	public ByteBuffer getByteBuffer() {
		return _backBuffer;
	}

	@Override
	public void destroy() {
		//do nothing
	}
}
