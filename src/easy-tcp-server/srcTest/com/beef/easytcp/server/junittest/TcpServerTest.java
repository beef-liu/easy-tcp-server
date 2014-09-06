package com.beef.easytcp.server.junittest;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.Test;

import com.beef.easytcp.base.ChannelByteBuffer;
import com.beef.easytcp.base.ChannelByteBufferPoolFactory;
import com.beef.easytcp.server.TcpServer;
import com.beef.easytcp.server.config.TcpServerConfig;

public class TcpServerTest {
	
	public void main(String[] args) {
		TcpServerConfig serverConfig = new TcpServerConfig();
		serverConfig.setHost("127.0.0.1");
		serverConfig.setPort(6379);
		serverConfig.setConnectMaxCount(128);
		serverConfig.setConnectTimeout(5000);
		serverConfig.setConnectWaitCount(16);
		serverConfig.setSocketIOThreadCount(2);
		serverConfig.setSocketReceiveBufferSize(1024*16);
		serverConfig.setSocketSendBufferSize(1024*16);
		
		TcpServer server = new TcpServer(serverConfig, false, workerDispatcher);
	}


}
