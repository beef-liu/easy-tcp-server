package com.beef.easytcp.base.handler;

import com.beef.easytcp.base.IByteBuff;

public interface ITcpReplyMessageHandler {
	
	/**
	 * Buffer created by this method is only for temporary variable for sendMessage().
	 * Warning:
	 * It will be destroyed when sending finished. 
	 * And do not reuse it in multiple times of invoking sendMessage(),
	 * Because sendMessage() is executing asynchronously in write event thread. 
	 * @return
	 */
	public IByteBuff createBuffer();
	
	/**
	 * send back message to remote peer
	 * @param msg
	 */
	public void sendMessage(IByteBuff msg);
	
}
