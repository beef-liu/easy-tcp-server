package com.beef.easytcp.base.handler;

import java.net.SocketAddress;

import com.beef.easytcp.base.IByteBuff;

public interface ITcpEventHandler extends ITcpReadEventHandler {

	/**
	 * Event of socket connection constructed
	 * @param remoteAddress SocketAddress of remote peer maybe is useful information
	 * @param replyMessageHandler For sending reply message to remote peer
	 */
	public void didConnect(ITcpReplyMessageHandler replyMessageHandler, SocketAddress remoteAddress);

	/**
	 * Event of socket connection closed
	 */
	public void didDisconnect();
	
}
