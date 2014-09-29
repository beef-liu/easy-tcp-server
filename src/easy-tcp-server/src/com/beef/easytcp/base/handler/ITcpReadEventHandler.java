package com.beef.easytcp.base.handler;

import com.beef.easytcp.base.IByteBuff;

public interface ITcpReadEventHandler {

	/**
	 * Event of message which sent by remote peer was received
	 * @param replyMessageHandler 
	 * @param msgs
	 */
	public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, MessageList<IByteBuff> msgs);
}
