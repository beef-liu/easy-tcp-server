package com.beef.easytcp.junittest;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.junit.Test;

public class ByteBufferTest {

	@Test
	public void test() {
		try {
			int capacity = 1024;
			ByteBuffer byteBuff = ByteBuffer.allocate(capacity);
			
			byteBuff.clear();
			outputByteBufferStatus("after clear", byteBuff);
			
			byteBuff.flip();
			outputByteBufferStatus("after flip ", byteBuff);

			byteBuff.clear();
			outputByteBufferStatus("after clear", byteBuff);
			
//			byteBuff.flip();
//			outputByteBufferStatus("after flip ", byteBuff);
			
			byte[] bytes = new byte[24];
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			
			ReadableByteChannel readChannel = Channels.newChannel(bis);
			WritableByteChannel writeChannel = Channels.newChannel(bos);
			
			int readCnt = readChannel.read(byteBuff);
			outputByteBufferStatus("after read " + readCnt + " byte into ", byteBuff);
			
			byteBuff.flip();
			outputByteBufferStatus("after flip ", byteBuff);
			
			int writeCnt = writeChannel.write(byteBuff);
			outputByteBufferStatus("after write " + writeCnt + " byte from ", byteBuff);
			
			byteBuff.clear();
			outputByteBufferStatus("after clear", byteBuff);
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}

	private static void outputByteBufferStatus(String msgPrefix, ByteBuffer byteBuff) {
		System.out.println(msgPrefix + " bytebuffer position:" + byteBuff.position() 
				+ " limit:" + byteBuff.limit() 
				+ " remaining:" + byteBuff.remaining());
	}
}
