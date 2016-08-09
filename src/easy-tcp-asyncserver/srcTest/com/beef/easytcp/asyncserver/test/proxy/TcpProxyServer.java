package com.beef.easytcp.asyncserver.test.proxy;

import com.beef.easytcp.asyncclient.AsyncTcpClient;
import com.beef.easytcp.asyncserver.AsyncTcpServer;
import com.beef.easytcp.asyncserver.handler.IAsyncSession;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuff;
import com.beef.easytcp.asyncserver.test.TcpClient;
import com.beef.easytcp.asyncserver.test.proxy.config.TcpProxyServerConfig;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

public class TcpProxyServer implements Closeable {
    private final static Logger logger = Logger.getLogger(TcpProxyServer.class);


    private final TcpProxyServerConfig _config;
    private AsyncTcpServer _tcpServer;

    private IByteBuffProvider _byteBuffProvider;

    public TcpProxyServer(TcpProxyServerConfig config) {
        _config = config;
        boolean isAllocateDirect = true;

        initByteBuffProvider(true);

        _tcpServer = new AsyncTcpServer(
                _config.getTcpServerConfig(),
                isAllocateDirect,
                new ITcpEventHandlerFactory() {
                    @Override
                    public ITcpEventHandler createHandler(int sessionId) {
                        return new MyTcpEventHandler(sessionId);
                    }
                },
                _byteBuffProvider
        );
        _tcpServer.start();
    }

    @Override
    public void close() throws IOException {
        try {
            _tcpServer.shutdown();
        } catch (Throwable e) {
            logger.error(null, e);
        }

        try {
            _byteBuffProvider.close();
        } catch (Throwable e) {
            logger.error(null, e);
        }
    }

    private void initByteBuffProvider(boolean isAllocateDirect) {
        int bufferByteSize = _config.getTcpServerConfig().getSocketReceiveBufferSize();

        GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
        byteBufferPoolConfig.setMaxIdle(_config.getTcpServerConfig().getConnectMaxCount());
        byteBufferPoolConfig.setMaxTotal(_config.getTcpServerConfig().getConnectMaxCount() * 5);
        byteBufferPoolConfig.setMaxWaitMillis(1000);

        //byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
        //byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);

        final ByteBufferPool bufferPool = new ByteBufferPool(
                byteBufferPoolConfig, isAllocateDirect, bufferByteSize);
        _byteBuffProvider = new IByteBuffProvider() {
            @Override
            public IByteBuff createBuffer() {
                return bufferPool.borrowObject();
            }

            @Override
            public void close() throws IOException {
                bufferPool.close();
            }
        };
    }

    private ITcpEventHandlerFactory _eventHandlerFactory = new ITcpEventHandlerFactory() {
        @Override
        public ITcpEventHandler createHandler(int sessionId) {
            return null;
        }
    };

    private class MyTcpEventHandler implements ITcpEventHandler {
        private int _sessionId;
        //private AsyncTcpClient _tcpClient;
        private TcpClient _tcpClient;
        private IAsyncSession _session;

        public MyTcpEventHandler(int sessionId) {
            _sessionId = sessionId;
        }

        @Override
        public void didConnect(ITcpReplyMessageHandler replyMessageHandler, SocketAddress socketAddress) {
            //init tcp client
            try {
                _tcpClient = new TcpClient(_config.getBackendSetting().getTcpClientConfig());
                //_tcpClient = new AsyncTcpClient(_config.getBackendSetting().getTcpClientConfig(), _byteBuffProvider);
                _tcpClient.connect();

                _session = _tcpServer.getSession(_sessionId);

                //start read thread
                Thread readThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int readByteLen;
                        while (true) {
                            IByteBuff buff = _byteBuffProvider.createBuffer();
                            try {
                                readByteLen = _tcpClient.receive(buff.getByteBuffer());
                                if(readByteLen <= 0) {
                                    buff.destroy();
                                } else {
                                    //send back
                                    buff.getByteBuffer().flip();
                                    _session.addWriteEvent(new AsyncWriteEvent4ByteBuff(buff));
                                    logger.debug("add data to write. dataLen:" + readByteLen);
                                }
                            } catch (SocketTimeoutException e) {
                            	buff.destroy();
                            } catch (Throwable e) {
                                logger.debug("client readThread runLoop end", e);
                                buff.destroy();
                                break;
                            }
                        }
                    }
                });
                readThread.start();
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
                //request redirect to backend
                buff.getByteBuffer().flip();
                _tcpClient.send(buff.getByteBuffer());
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }

    }


}
