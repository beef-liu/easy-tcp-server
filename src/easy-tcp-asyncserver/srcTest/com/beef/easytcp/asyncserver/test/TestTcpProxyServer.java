package com.beef.easytcp.asyncserver.test;

import com.beef.easytcp.asyncserver.test.proxy.TcpProxyServer;
import com.beef.easytcp.asyncserver.test.proxy.config.BackendSetting;
import com.beef.easytcp.asyncserver.test.proxy.config.TcpProxyServerConfig;
import com.beef.easytcp.client.TcpClientConfig;
import com.beef.easytcp.server.TcpServerConfig;

import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class TestTcpProxyServer {

    private final static Logger logger = Logger.getLogger(TestTcpProxyServer.class);

    public static void main(String[] args) {
        int maxConnection = 10000;
        int ioThreadCount = 4;
        int readEventThreadCount = 4;
        int SocketReceiveBufferSize = 16 * 1024;

        boolean isAllocateDirect = true;


        String hostRedirectTo = "127.0.0.1";
        int portRedirectTo = 6379;


        TcpProxyServerConfig config = new TcpProxyServerConfig();

        {
            TcpServerConfig serverConfig = new TcpServerConfig();
            serverConfig.setHost("127.0.0.1");
            serverConfig.setPort(16379);
            serverConfig.setConnectMaxCount(maxConnection);
            serverConfig.setConnectTimeout(1000);
            serverConfig.setSoTimeout(5000);
            serverConfig.setConnectWaitCount(maxConnection);
            serverConfig.setSocketIOThreadCount(ioThreadCount);
            serverConfig.setSocketReceiveBufferSize(SocketReceiveBufferSize);
            serverConfig.setSocketSendBufferSize(SocketReceiveBufferSize);
            serverConfig.setReadEventThreadCount(readEventThreadCount);
            serverConfig.setWriteEventThreadCount(ioThreadCount);

            config.setTcpServerConfig(serverConfig);
        }

        {
            BackendSetting backendSetting = new BackendSetting();
            {
                TcpClientConfig tcpClientConfig = new TcpClientConfig();
                tcpClientConfig.setHost(hostRedirectTo);
                tcpClientConfig.setPort(portRedirectTo);
                tcpClientConfig.setConnectTimeoutMS(3000);
                tcpClientConfig.setSoTimeoutMS(1000);
                tcpClientConfig.setReceiveBufferSize(SocketReceiveBufferSize);
                tcpClientConfig.setSendBufferSize(SocketReceiveBufferSize);
                tcpClientConfig.setTcpNoDelay(false);

                backendSetting.setTcpClientConfig(tcpClientConfig);
            }


            config.setBackendSetting(backendSetting);
        }

        final TcpProxyServer proxyServer = new TcpProxyServer(
                config
        );
        
		//handle kill signal ----------------------
        Runtime.getRuntime().addShutdownHook(new Thread() {
        	@Override
        	public void run() {
        		try {
        			logger.info(" received kill signal. ------");
        			//System.out.println(getProgramName() + " received kill signal. ------");
        			
        			proxyServer.close();
        		} catch(Throwable e) {
        			e.printStackTrace();
        			logger.error(null, e);
        		}
        	}
        });

        proxyServer.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        
//        try {
//            Thread.sleep(300 * 1000);
//            proxyServer.close();
//        } catch (Throwable e) {
//            logger.error(null, e);
//        }
        
        logger.debug("TestTcpProxyServer end --------");
    }
}
