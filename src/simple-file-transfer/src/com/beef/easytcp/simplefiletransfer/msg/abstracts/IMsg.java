package com.beef.easytcp.simplefiletransfer.msg.abstracts;

import java.nio.ByteBuffer;

public interface IMsg {

	/**
	 * Write contents into buffer
	 * @param buffer
	 * @param offset
	 * @return Byte length which has been written
	 */
	public int write(ByteBuffer buffer);
	
	/**
	 * Read contents from buffer
	 * @param buffer
	 * @param offset
	 * @return Byte length which has been read
	 */
	public int read(ByteBuffer buffer);
}

