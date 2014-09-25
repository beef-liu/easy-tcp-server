package com.beef.easytcp;

public interface ITcpEventHandler {
	
	public void didConnect(SelectionKeyWrapper selectionKey);
	
	public void didDisconnect(SelectionKeyWrapper selectionKey);
	
	public void didReceivedMsg(
			SelectionKeyWrapper selectionKey, 
			MessageList<? extends ByteBuff> messages);
	
	public void didReceivedMsg(
			SelectionKeyWrapper selectionKey, 
			ByteBuff message);
}
