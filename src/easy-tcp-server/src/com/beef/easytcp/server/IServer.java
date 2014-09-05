package com.beef.easytcp.server;

import java.io.IOException;

/**
 * It just for illustrating interface explicitly.  
 * @author XingGu Liu
 *
 */
public interface IServer {
	void start() throws IOException;
	
	void shutdown();
}
