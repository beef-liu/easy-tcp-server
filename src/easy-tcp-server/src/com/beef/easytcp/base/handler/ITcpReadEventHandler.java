package com.beef.easytcp.base.handler;

import com.beef.easytcp.base.IByteBuff;

public interface ITcpReadEventHandler {

	/**
	 * Event of message which sent by remote peer was received
	 * @param replyMessageHandler 
	 * @param msgs
	 */
	public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, MessageList<? extends IByteBuff> msgs);
	
	/**
	 * Event of message which sent by remote peer was received
	 * warning: 
	 * parameter msg is only for reading, it's a temporary variables which means it will be destroyed right after didReceiveMessage() done.
	 * @param msg
	 */
	public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, IByteBuff msg);
	
}
