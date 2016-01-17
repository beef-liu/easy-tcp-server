package com.beef.easytcp.simplefiletransfer.msg.abstracts;

import java.nio.ByteBuffer;
import java.util.Random;

import com.beef.easytcp.simplefiletransfer.util.BitUtil;

public class MsgHead {

	public final static byte[] MsgBegin2Bytes = {
		0x18, 0x7f,
	};
	
	public final static int MsgHeadByteLen = 28;
	
	private final static Random _rand = new Random(System.currentTimeMillis());

	protected byte _msgBegin0 = MsgBegin2Bytes[0];
	protected byte _msgBegin1 = MsgBegin2Bytes[1];
	protected byte _msgBegin2;
	protected byte _msgBegin3;

	//4
	protected int _msgLen = 0;
	//8
	protected short _msgType = 0;
	//10
	protected long _timestamp = 0;
	//14
	protected byte _compressed = 0;
	//15
	protected byte _reserved0 = 0;
	//16
	protected int _reserved1 = 0;
	//20
	protected int _reserved2 = 0;
	
	//headLen = 24

	public static boolean isValidMsgBegin(byte[] buffer, int offset) {
		if(buffer[offset] != MsgBegin2Bytes[0]) {
			return false;
		}
		if(buffer[offset + 1] != MsgBegin2Bytes[1]) {
			return false;
		}
		
		byte msgBegin2 = buffer[offset + 2];
		
		byte msgBegin3 = (byte) BitUtil.countBit1(msgBegin2);
		if(buffer[offset + 3] != msgBegin3) {
			return false;
		}
		
		return true;
	}
	
	public static int getMsgLenFromBuffer(byte[] buffer, int offset) {
		int pos = offset + 4;
		return ((buffer[pos] << 24) & 0xff000000) 
				| ((buffer[pos + 1] << 16) & 0x00ff0000)
				| ((buffer[pos + 2] << 8) & 0x0000ff00)
				| ((buffer[pos + 3]) & 0x000000ff)
				;
	}

	public static void setMsgLenIntoBuffer(int msgLen, byte[] buffer, int offset) {
		int pos = offset + 4;
		
		buffer[pos] = (byte) ((msgLen >> 24) & 0x000000ff);
		buffer[pos + 1] = (byte) ((msgLen >> 16) & 0x000000ff);
		buffer[pos + 2] = (byte) ((msgLen >> 8) & 0x000000ff);
		buffer[pos + 3] = (byte) (msgLen & 0x000000ff);
	}
	
	/*
	public static short getMsgType(byte[] buffer, int offset) {
		int pos = offset + 8;
		return (short) (((buffer[pos] << 8) & 0xff00) 
				| (buffer[pos + 1] & 0x00ff));
	}
	
	public static void setMsgType(short msgType, byte[] buffer, int offset) {
		int pos = offset + 8;
		buffer[pos] = (byte) ((msgType >> 8) & 0x000000ff);
		buffer[pos + 1] = (byte) (msgType & 0x000000ff);
	}
	
	public static int getTick(byte[] buffer, int offset) {
		int pos = offset + 10;
		return ((buffer[pos] << 24) & 0xff000000) 
				| ((buffer[pos + 1] << 16) & 0x00ff0000)
				| ((buffer[pos + 2] << 8) & 0x0000ff00)
				| ((buffer[pos + 3]) & 0x000000ff)
				;
	}
	*/
	
	public MsgHead() {
	}
	
	public void generateMsgBegin() {
		_msgBegin2 = (byte) _rand.nextInt(128);
		
		//bit count
		_msgBegin3 = (byte) BitUtil.countBit1(_msgBegin2);
	} 
	

	public int readMsgHead(ByteBuffer buffer) {
		int beginPos = buffer.position();
		
		_msgBegin0 = buffer.get();
		_msgBegin1 = buffer.get();
		_msgBegin2 = buffer.get();
		_msgBegin3 = buffer.get();
		
		_msgLen = buffer.getInt();
		_msgType = buffer.getShort();
		
		_timestamp = buffer.getLong();
		
		_compressed = buffer.get();
		_reserved0 = buffer.get();
		
		_reserved1 = buffer.getInt();
		_reserved2 = buffer.getInt();
		
		return (buffer.position() - beginPos); 
	}
	
	public int writeMsgHead(ByteBuffer buffer) {
		int beginPos = buffer.position();
		
		buffer.put(_msgBegin0);
		buffer.put(_msgBegin1);
		buffer.put(_msgBegin2);
		buffer.put(_msgBegin3);
		
		buffer.putInt(_msgLen);
		buffer.putShort(_msgType);
		
		buffer.putLong(_timestamp);
		
		buffer.put(_compressed);
		buffer.put(_reserved0);
		
		buffer.putInt(_reserved1);
		buffer.putInt(_reserved2);
		
		return (buffer.position() - beginPos); 
	}

	public byte getMsgBegin0() {
		return _msgBegin0;
	}

	public void setMsgBegin0(byte msgBegin0) {
		_msgBegin0 = msgBegin0;
	}

	public byte getMsgBegin1() {
		return _msgBegin1;
	}

	public void setMsgBegin1(byte msgBegin1) {
		_msgBegin1 = msgBegin1;
	}

	public byte getMsgBegin2() {
		return _msgBegin2;
	}

	public void setMsgBegin2(byte msgBegin2) {
		_msgBegin2 = msgBegin2;
	}

	public byte getMsgBegin3() {
		return _msgBegin3;
	}

	public void setMsgBegin3(byte msgBegin3) {
		_msgBegin3 = msgBegin3;
	}

	public int getMsgLen() {
		return _msgLen;
	}

	public void setMsgLen(int msgLen) {
		_msgLen = msgLen;
	}

	public short getMsgType() {
		return _msgType;
	}

	public void setMsgType(short msgType) {
		_msgType = msgType;
	}

	public long getTimestamp() {
		return _timestamp;
	}

	public void setTimestamp(long timestamp) {
		_timestamp = timestamp;
	}

	public byte getCompressed() {
		return _compressed;
	}

	public void setCompressed(byte compressed) {
		_compressed = compressed;
	}

	public byte getReserved0() {
		return _reserved0;
	}

	public void setReserved0(byte reserved0) {
		_reserved0 = reserved0;
	}

	public int getReserved1() {
		return _reserved1;
	}

	public void setReserved1(int reserved1) {
		_reserved1 = reserved1;
	}

	public int getReserved2() {
		return _reserved2;
	}

	public void setReserved2(int reserved2) {
		_reserved2 = reserved2;
	}

	public static Random getRand() {
		return _rand;
	}

	
}
