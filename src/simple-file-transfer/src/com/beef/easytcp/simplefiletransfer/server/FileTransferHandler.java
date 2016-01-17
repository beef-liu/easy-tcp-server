package com.beef.easytcp.simplefiletransfer.server;

import java.net.SocketAddress;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;

public class FileTransferHandler implements ITcpEventHandler {
	private static enum ProcessStatus {
		NotConnected,
		Connected,
		TransferStartRequestReceived,
		TransferFinished,
	};
	
	private ProcessStatus _status = ProcessStatus.NotConnected;

	@Override
	public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler,
			MessageList<? extends IByteBuff> msgs) {
		for(IByteBuff msg : msgs) {
			didReceiveMessage(replyMessageHandler, msg);
		}
	}

	@Override
	public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler,
			IByteBuff msg) {
		msg.getByteBuffer().array()
		int msgType = 
		
	}

	@Override
	public void didConnect(ITcpReplyMessageHandler replyMessageHandler,
			SocketAddress remoteAddress) {
		_status = ProcessStatus.Connected;

		
	}

	@Override
	public void didDisconnect() {
		_status = ProcessStatus.NotConnected;
	}

}
