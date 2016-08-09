package com.beef.easytcp.asyncserver.test.proxy;

import com.beef.easytcp.asyncserver.AsyncTcpServer;
import com.beef.easytcp.asyncserver.test.TcpClient;
import com.beef.easytcp.asyncserver.test.proxy.config.TcpProxyServerConfig;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import org.apache.log4j.Logger;

import java.net.SocketAddress;

public class TcpProxyServer {
    private final static Logger logger = Logger.getLogger(TcpProxyServer.class);


    private final TcpProxyServerConfig _config;
    private AsyncTcpServer _tcpServer;

    public TcpProxyServer(TcpProxyServerConfig config) {
        _config = config;
        boolean isAllocateDirect = true;

        _tcpServer = new AsyncTcpServer(
                _config.getTcpServerConfig(),
                isAllocateDirect,


        );
    }

    private ITcpEventHandlerFactory _eventHandlerFactory = new ITcpEventHandlerFactory() {
        @Override
        public ITcpEventHandler createHandler(int sessionId) {
            return null;
        }
    };

    private class MyTcpEventHandler implements ITcpEventHandler {
        private int _sessionId;
        private TcpClient _tcpClient;

        public MyTcpEventHandler(int sessionId) {
            _sessionId = sessionId;
        }

        @Override
        public void didConnect(ITcpReplyMessageHandler iTcpReplyMessageHandler, SocketAddress socketAddress) {
            //init tcp client
            try {
                _tcpClient = new TcpClient(_config.getBackendSetting().getTcpClientConfig());
                _tcpClient.connect();
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }

        @Override
        public void didDisconnect() {
            //close tcp client
            try {
                _tcpClient.disconnect();
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }

        @Override
        public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, MessageList<? extends IByteBuff> messageList) {
            for(IByteBuff msg : messageList) {
                didReceiveMessage(replyMessageHandler, msg);
            }
        }

        @Override
        public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, IByteBuff buff) {
            try {
                //request to backend
                _tcpClient.send(buff.getByteBuffer());

                //response to invoker
                while() {
                    _tcpClient.receive()
                }
                buff.getByteBuffer().clear();
                _tcpClient.receive()
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }

    }


    public void start() {

    }


}
