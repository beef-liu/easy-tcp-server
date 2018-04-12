package com.beef.easytcp.client;

import java.io.IOException;

public interface ITcpClient {
	
	public void connect() throws IOException;
	
	public void disconnect() throws IOException;
	
	public boolean isConnected();
	
}
