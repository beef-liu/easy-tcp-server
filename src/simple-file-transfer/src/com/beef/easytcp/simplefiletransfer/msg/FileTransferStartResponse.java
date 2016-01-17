package com.beef.easytcp.simplefiletransfer.msg;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.beef.easytcp.simplefiletransfer.msg.abstracts.IMsg;
import com.beef.easytcp.simplefiletransfer.msg.abstracts.MsgHead;


public class FileTransferStartResponse extends MsgHead implements IMsg {
	private long _fileSeq;
	
	private long _fileLen;
	
	private int _filePieceLen;
	
	/**
	 * 64 bit represents 64 file pieces, the first left bit represent the index 0 in big endian.
	 */
	private List<IntClosedInterval> _vacantIndexIntervalList = new ArrayList<IntClosedInterval>();

	@Override
	public int write(ByteBuffer buffer) {
		int headLen = writeMsgHead(buffer);
		
		int bodyBegin = buffer.position();
	
		buffer.putLong(_fileSeq);
		buffer.putLong(_fileLen);
		buffer.putInt(_filePieceLen);
		
		for(IntClosedInterval interval : _vacantIndexIntervalList) {
			buffer.putInt(interval.getBegin());
			buffer.putInt(interval.getEnd());
		}
		
		return headLen + (buffer.position() - bodyBegin);
	}

	@Override
	public int read(ByteBuffer buffer) {
		int startPos = buffer.position();
		
		int headLen = readMsgHead(buffer);

		int posMax = startPos + _msgLen;
		
		int bodyBegin = buffer.position();
		
		_fileSeq = buffer.getLong();
		_fileLen = buffer.getLong();
		_filePieceLen = buffer.getInt();

		_vacantIndexIntervalList.clear();
		while(buffer.position() < posMax) {
			IntClosedInterval interval = new IntClosedInterval();
			
			interval.setBegin(buffer.getInt());
			interval.setEnd(buffer.getInt());
			
			_vacantIndexIntervalList.add(interval);
		}
		
		return headLen + (buffer.position() - bodyBegin);
	}
	

	
}
