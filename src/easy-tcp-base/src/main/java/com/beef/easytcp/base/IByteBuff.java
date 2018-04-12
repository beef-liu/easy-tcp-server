package com.beef.easytcp.base;

import java.nio.ByteBuffer;

public interface IByteBuff {
	
	public ByteBuffer getByteBuffer();
	
	public void destroy();
}
