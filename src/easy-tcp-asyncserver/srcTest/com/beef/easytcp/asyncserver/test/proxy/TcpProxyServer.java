package com.beef.easytcp.asyncserver.test.proxy;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.beef.easytcp.asyncclient.AsyncTcpClient;
import com.beef.easytcp.asyncserver.AsyncTcpServer;
import com.beef.easytcp.asyncserver.handler.IAsyncSession;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuff;
import com.beef.easytcp.asyncserver.test.TcpClient;
import com.beef.easytcp.asyncserver.test.proxy.config.TcpProxyServerConfig;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.buffer.PooledByteBuffer;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;

public class TcpProxyServer implements Closeable {
    private final static Logger logger = Logger.getLogger(TcpProxyServer.class);


    private final TcpProxyServerConfig _config;
    private AsyncTcpServer _tcpServer;

    private IByteBuffProvider _byteBuffProvider;
    
    private AsynchronousChannelGroup _channelGroup;

    public TcpProxyServer(
    		TcpProxyServerConfig config
    		) throws IOException {
        _config = config;
        boolean isAllocateDirect = true;
        initByteBuffProvider(isAllocateDirect);

        try {
            _channelGroup = AsynchronousChannelGroup.withThreadPool(
            		Executors.newCachedThreadPool()
            		);
        } catch (IOException e) {
        	close();
        	throw e;
        }

        _tcpServer = new AsyncTcpServer(
                _config.getTcpServerConfig(),
                isAllocateDirect,
                new ITcpEventHandlerFactory() {
                    @Override
                    public ITcpEventHandler createHandler(int sessionId) {
                        //return new MyTcpEventHandlerOnSyncTcpClient(sessionId);
                    	return new MyTcpEventHandlerOnAsyncTcpClient(sessionId);
                    }
                },
                _byteBuffProvider,
                _channelGroup
        );
    }
    
    public void awaitTermination(long time, TimeUnit timeUnit) {
    	_tcpServer.awaitTermination(time, timeUnit);
    }
    
    public void start() {
        _tcpServer.start();
    }
    
    public void shutdown() {
        _tcpServer.shutdown();
    }

    @Override
    public void close() throws IOException {
    	logger.info("TcpProxyServer closing -------");

    	try {
    		if(_tcpServer.isServerChannelOpen()) {
    			_tcpServer.shutdown();
    		}
    	} catch (Throwable e) {
    		logger.error(null, e);
    	}
    	
    	try {
            _channelGroup.shutdown();
    	} catch (Throwable e) {
    		logger.error(null, e);
    	}

    	try {
            _byteBuffProvider.close();
    	} catch (Throwable e) {
    		logger.error(null, e);
    	}
    	
    	logger.info("TcpProxyServer closed <<<<<<<<");
    }

    private void initByteBuffProvider(boolean isAllocateDirect) {
        int bufferByteSize = _config.getTcpServerConfig().getSocketReceiveBufferSize();

        GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
        byteBufferPoolConfig.setMaxIdle(_config.getTcpServerConfig().getConnectMaxCount());
        byteBufferPoolConfig.setMaxTotal(_config.getTcpServerConfig().getConnectMaxCount() * 3);
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

    private class MyTcpEventHandlerOnAsyncTcpClient implements ITcpEventHandler {
        private int _sessionId;
        private AsyncTcpClient _tcpClient;
        private IAsyncSession _session;

        public MyTcpEventHandlerOnAsyncTcpClient(int sessionId) {
            _sessionId = sessionId;
        }


        @Override
        public void didConnect(ITcpReplyMessageHandler iTcpReplyMessageHandler, SocketAddress socketAddress) {
            //init tcp client
            try {
                _session = _tcpServer.getSession(_sessionId);

                _tcpClient = new AsyncTcpClient(
                        _config.getBackendSetting().getTcpClientConfig(),
                        _byteBuffProvider,
                        _channelGroup
                );
                _tcpClient.setEventHandler(_clientEventHandler);
                //_tcpClient.connect();
                _tcpClient.syncConnect();
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }

        private ITcpEventHandler _clientEventHandler = new ITcpEventHandler() {
            @Override
            public void didConnect(ITcpReplyMessageHandler iTcpReplyMessageHandler, SocketAddress socketAddress) {
            }

            @Override
            public void didDisconnect() {
            }

            @Override
            public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, MessageList<? extends IByteBuff> messageList) {
            	for(IByteBuff buff : messageList) {
            		didReceiveMessage(replyMessageHandler, buff);
            	}
            }

            @Override
            public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, IByteBuff buff) {
                //send back
                buff.getByteBuffer().flip();

                if(PooledByteBuffer.class.isAssignableFrom(buff.getClass())) {
                    ((PooledByteBuffer)buff).setDeferredDestroy(true);
                    _session.addWriteEvent(new AsyncWriteEvent4ByteBuff(buff));
                } else {
                    //copy the buffer
                    IByteBuff buff2 = _byteBuffProvider.createBuffer();
                    buff2.getByteBuffer().clear();
                    buff2.getByteBuffer().put(buff.getByteBuffer());

                    buff2.getByteBuffer().flip();
                    _session.addWriteEvent(new AsyncWriteEvent4ByteBuff(buff2));
                }
            }
        };

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

                if(PooledByteBuffer.class.isAssignableFrom(buff.getClass())) {
                    ((PooledByteBuffer)buff).setDeferredDestroy(true);
                    _tcpClient.send(buff);
                } else {
                    //copy the buffer
                    IByteBuff buff2 = _byteBuffProvider.createBuffer();
                    buff2.getByteBuffer().clear();
                    buff2.getByteBuffer().put(buff.getByteBuffer());

                    buff2.getByteBuffer().flip();
                    _tcpClient.send(buff2);
                }


            } catch (Throwable e) {
                logger.error(null, e);
            }
        }
    }

    private class MyTcpEventHandlerOnSyncTcpClient implements ITcpEventHandler {
        private int _sessionId;
        private TcpClient _tcpClient;
        private IAsyncSession _session;

        public MyTcpEventHandlerOnSyncTcpClient(int sessionId) {
            _sessionId = sessionId;
        }

        @Override
        public void didConnect(ITcpReplyMessageHandler replyMessageHandler, SocketAddress socketAddress) {
            //init tcp client
            try {
                _session = _tcpServer.getSession(_sessionId);

                _tcpClient = new TcpClient(_config.getBackendSetting().getTcpClientConfig());
                //_tcpClient = new AsyncTcpClient(_config.getBackendSetting().getTcpClientConfig(), _byteBuffProvider);
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
                //request redirect to backend
                buff.getByteBuffer().flip();
                _tcpClient.send(buff.getByteBuffer());
                
                //receive response
                receiveResponseFromBackend(replyMessageHandler);
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }
        
        private void receiveResponseFromBackend(ITcpReplyMessageHandler replyMessageHandler) {
        	long beginTime = System.currentTimeMillis();
        	long totalReadLen = 0;
            int readByteLen;
        	logger.debug("receiveResponseFromBackend start -----------------" );
            while (true) {
                IByteBuff buff = _byteBuffProvider.createBuffer();
                buff.getByteBuffer().clear();
                
                try {
                    readByteLen = _tcpClient.receive(buff.getByteBuffer());
                    if(readByteLen <= 0) {
                        buff.destroy();
                        
                        long elapsedTime = System.currentTimeMillis() - beginTime;
                        if(totalReadLen == 0 
                        		&& elapsedTime <= _config.getBackendSetting().getTcpClientConfig().getSoTimeoutMS()
                        	) {
                        	Thread.sleep(0, 1000);
                        	continue;
                        } else {
                            break;
                        }
                    } else {
                    	totalReadLen += readByteLen;
                    	
                        //send back
                        buff.getByteBuffer().flip();
                        _session.addWriteEvent(new AsyncWriteEvent4ByteBuff(buff));
                        //logger.debug("add data to write. dataLen:" + readByteLen);
                    }
                } catch (SocketTimeoutException e) {
                	buff.destroy();
                	break;
                } catch (Throwable e) {
                    logger.debug("client readThread runLoop end", e);
                    buff.destroy();
                    break;
                }
            }
        	logger.debug("receiveResponseFromBackend end   <<<<<<<<<<<<<<<<<" );
        }

    }


}
