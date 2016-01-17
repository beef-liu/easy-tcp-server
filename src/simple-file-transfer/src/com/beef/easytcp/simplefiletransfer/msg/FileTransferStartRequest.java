package com.beef.easytcp.simplefiletransfer.msg;

import java.nio.ByteBuffer;

import com.beef.easytcp.simplefiletransfer.msg.abstracts.IMsg;
import com.beef.easytcp.simplefiletransfer.msg.abstracts.MsgHead;


public class FileTransferStartRequest extends MsgHead implements IMsg {
	
	private long _fileSeq;
	
	private long _fileLen;
	
	private int _filePieceMaxLen;
	
	@Override
	public int write(ByteBuffer buffer) {
		int headLen = writeMsgHead(buffer);
		
		int bodyBegin = buffer.position();
	
		buffer.putLong(_fileSeq);
		buffer.putLong(_fileLen);
		buffer.putInt(_filePieceMaxLen);
		
		return headLen + (buffer.position() - bodyBegin);
	}

	@Override
	public int read(ByteBuffer buffer) {
		int headLen = readMsgHead(buffer);
		
		int bodyBegin = buffer.position();
		
		_fileSeq = buffer.getLong();
		_fileLen = buffer.getLong();
		_filePieceMaxLen = buffer.getInt();
		
		return headLen + (buffer.position() - bodyBegin);
	}
	

	public long getFileSeq() {
		return _fileSeq;
	}

	public void setFileSeq(long fileSeq) {
		_fileSeq = fileSeq;
	}

	public long getFileLen() {
		return _fileLen;
	}

	public void setFileLen(long fileLen) {
		_fileLen = fileLen;
	}

	public int getFilePieceLen() {
		return _filePieceMaxLen;
	}

	public void setFilePieceLen(int filePieceLen) {
		_filePieceMaxLen = filePieceLen;
	}

}
