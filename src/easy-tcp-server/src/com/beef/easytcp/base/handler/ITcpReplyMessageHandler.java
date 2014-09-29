package com.beef.easytcp.base.handler;

import com.beef.easytcp.base.IByteBuff;

public interface ITcpReplyMessageHandler {
	
	/**
	 * send back message to remote peer
	 * @param msg
	 */
	public void sendMessage(IByteBuff msg);
	
}
