package com.beef.easytcp.asyncserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import com.beef.easytcp.asyncserver.handler.AsyncTcpSession;
import com.beef.easytcp.asyncserver.handler.IAsyncSession;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.buffer.ByteBufferPool2;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.server.IServer;
import com.beef.easytcp.server.TcpServerConfig;

import simplepool.base.BasePoolConfig;

public class AsyncTcpServer implements IServer {
    private final static Logger logger = Logger.getLogger(AsyncTcpServer.class);

	static {
		System.out.println(
				AsyncTcpServer.class.getName() 
				+ " VERSION:" + "1.0.0" 
				+ " Date:" + "2016-08-18"
				);
	}
    
    protected final TcpServerConfig _tcpServerConfig;
    protected final boolean _isAllocateDirect;
    protected final ITcpEventHandlerFactory _eventHandlerFactory;

    protected final boolean _isByteBuffProviderFromArgs;
    protected IByteBuffProvider _byteBuffProvider = null;
    protected final boolean _isChannelGroupFromArgs;
    protected AsynchronousChannelGroup _channelGroup = null;
    
    protected AsynchronousServerSocketChannel _serverSocketChannel = null;

    //private AtomicInteger _clientSelectorCount = new AtomicInteger(0);
    protected final AtomicInteger _sessionIdSeed = new AtomicInteger(0);
    //protected AtomicInteger _connecttingSocketCount = new AtomicInteger(0);
    protected final Map<Integer, AsyncTcpSession> _sessionMap = new ConcurrentHashMap<Integer, AsyncTcpSession>();


    public AsyncTcpServer(
            TcpServerConfig tcpServerConfig,
            boolean isAllocateDirect,
            ITcpEventHandlerFactory eventHandlerFactory
            //boolean isSyncInvokeDidReceivedMsg
    ) {
        this(tcpServerConfig, isAllocateDirect, eventHandlerFactory, null, null);
    }

    public AsyncTcpServer(
            TcpServerConfig tcpServerConfig,
            boolean isAllocateDirect,
            ITcpEventHandlerFactory eventHandlerFactory,
            IByteBuffProvider byteBuffProvider,
            AsynchronousChannelGroup channelGroup
    ) {
        _tcpServerConfig = tcpServerConfig;

        //if use ByteBuffer.allocateDirect(), then there is no backing array which means ByteBuffer.array() is null.
        _isAllocateDirect = isAllocateDirect;

        _eventHandlerFactory = eventHandlerFactory;

        if(byteBuffProvider != null) {
            _isByteBuffProviderFromArgs = true;
        } else {
        	_isByteBuffProviderFromArgs = false;
        }
        _byteBuffProvider = byteBuffProvider;

        if(channelGroup != null) {
        	_isChannelGroupFromArgs = true;
        } else {
        	_isChannelGroupFromArgs = false;
        }
        _channelGroup = channelGroup;
        
    }

    public AsynchronousChannelGroup getChannelGroup() {
        return _channelGroup;
    }

    @Override
    public void start() {
        logger.info("AsyncTcpServer start ----------");

        try {
            startTcpServer();
        } catch(Throwable e) {
            logger.error("start()", e);
            shutdown();
        }

        logger.info("AsyncTcpServer start done <<<<<");
    }

    @Override
    public void shutdown() {
        logger.info("AsyncTcpServer shutdow -----------");

        //close all work channels
        try {
            Collection<AsyncTcpSession> sessions = _sessionMap.values();
            for(AsyncTcpSession session : sessions) {
                try {
                    session.close();
                } catch (Throwable e) {
                    logger.error(null, e);
                }
            }
        } catch (Throwable e) {
            logger.error(null, e);
        }

        //close server channel
        try {
            _serverSocketChannel.close();
            logger.debug("_serverSocketChannel close ---");
        } catch (Throwable e) {
            logger.error(null, e);
        }

        try {
            if(!_isByteBuffProviderFromArgs) {
                _byteBuffProvider.close();
                logger.debug("_byteBuffProvider close ---");
            }
        } catch (Throwable e) {
            logger.error(null, e);
        }
        
    	try {
            if(!_isChannelGroupFromArgs) {
            	_channelGroup.shutdown();
                logger.debug("_channelGroup close ---");
            }
        } catch (Throwable e) {
            logger.error(null, e);
    	}


        logger.info("AsyncTcpServer shutdown done <<<<<");
    }
    
    public boolean isServerChannelOpen() {
    	return _serverSocketChannel.isOpen();
    }

    public void awaitTermination(long time, TimeUnit timeUnit) {
        try {
            _channelGroup.awaitTermination(time, timeUnit);
        } catch (InterruptedException e) {
            logger.info("awaitTermination() interrupted");
        }
    }

    public IAsyncSession getSession(int sessionId) {
        return _sessionMap.get(sessionId);
    }

    private void startTcpServer() throws IOException {
        initByteBufferPool();

        initServerSocket();
    }

    private void initServerSocket() throws IOException {
        _sessionMap.clear();

        //channel group
        if(!_isChannelGroupFromArgs) {
        	/*
            int totalThreadCount = _tcpServerConfig.getSocketIOThreadCount()
                    + _tcpServerConfig.getReadEventThreadCount()
                    + _tcpServerConfig.getWriteEventThreadCount()
                    ;
            _channelGroup = AsynchronousChannelGroup.withThreadPool(
                    Executors.newFixedThreadPool(totalThreadCount)
            );
            */
        	_channelGroup = AsynchronousChannelGroup.withThreadPool(
        			Executors.newCachedThreadPool()
        	);
        }

        //channel open
        _serverSocketChannel = AsynchronousServerSocketChannel.open(_channelGroup);

        //socket options -------------------
        _serverSocketChannel.setOption(
                StandardSocketOptions.SO_REUSEADDR, true
        );

        _serverSocketChannel.setOption(
                StandardSocketOptions.SO_RCVBUF,
                _tcpServerConfig.getSocketReceiveBufferSize()
        );

        //channel bind ---------------------
        _serverSocketChannel.bind(
                new InetSocketAddress(_tcpServerConfig.getHost(), _tcpServerConfig.getPort()),
                _tcpServerConfig.getConnectWaitCount()
        );

        //accept asynchronously
        _serverSocketChannel.accept(
                null,
                _acceptCompletionHandler
        );
    }

    private void initByteBufferPool() {
        if(!_isByteBuffProviderFromArgs) {
            //init bytebuffer pool -----------------------------
//			ByteBufferPoolFactory byteBufferPoolFactory = new ByteBufferPoolFactory(
//					_isAllocateDirect, bufferByteSize);
            boolean isAllocateDirect = _isAllocateDirect;
            int bufferByteSize = _tcpServerConfig.getSocketReceiveBufferSize();
            
            int minIdle = _tcpServerConfig.getConnectMaxCount();
            int maxIdle = minIdle * 2;
            int maxTotal = maxIdle * 2;
            

            _byteBuffProvider = createDefaultByteBufferPool(bufferByteSize, isAllocateDirect, maxTotal, minIdle, maxIdle);
        }
    }
    
    public static IByteBuffProvider createDefaultByteBufferPool(
    		int bufferByteSize, boolean isAllocateDirect,
    		int maxTotal, int minIdle, int maxIdle
    		) {
        BasePoolConfig poolConfig = new BasePoolConfig();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setTestWhileIdle(false);
    	
        final ByteBufferPool2 bufferPool = new ByteBufferPool2(
        		poolConfig, isAllocateDirect, bufferByteSize
        		);
        return new IByteBuffProvider() {
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

    
    private CompletionHandler<AsynchronousSocketChannel, Object> _acceptCompletionHandler =
            new CompletionHandler<AsynchronousSocketChannel, Object>() {

                @Override
                public void completed(AsynchronousSocketChannel workChannel, Object attachment) {
                    //accept next client
                    _serverSocketChannel.accept(attachment, this);

                    try {
                        handleAcceptedWorkChannel(workChannel);
                    } catch (Throwable e) {
                        logger.error(null, e);
                    }
                }

                @Override
                public void failed(Throwable e, Object attachment) {
                	if(e.getClass() == AsynchronousCloseException.class) {
                        logger.info("ServerSocketChannel closed");
                	} else {
                        logger.error("ServerSocket accept failed", e);
                	}
                }

            };

    protected void handleAcceptedWorkChannel(AsynchronousSocketChannel workChannel) throws IOException {
        //set workChannel options
        if(workChannel == null) {
            logger.error("ServerSocket accept failed. workChannel is null.");
            return;
        }

        workChannel.setOption(
                StandardSocketOptions.SO_REUSEADDR, true
        );
        workChannel.setOption(
                StandardSocketOptions.TCP_NODELAY,
                _tcpServerConfig.isTcpNoDelay()
        );

        workChannel.setOption(
                StandardSocketOptions.SO_RCVBUF,
                _tcpServerConfig.getSocketReceiveBufferSize()
        );
        workChannel.setOption(
                StandardSocketOptions.SO_SNDBUF,
                _tcpServerConfig.getSocketSendBufferSize()
        );


        //build session
        final int sessionId = _sessionIdSeed.incrementAndGet();
        AsyncTcpSession session = new AsyncTcpSession(
                workChannel,
                sessionId,
                new MyTcpEventHandler(sessionId),
                _byteBuffProvider
        );
        _sessionMap.put(sessionId, session);
        session.resumeReadLoop();
    }

    private class MyTcpEventHandler implements ITcpEventHandler {
        private final int _sessionId;
        private final ITcpEventHandler _eventHandler;

        public MyTcpEventHandler(int sessionId) {
            _sessionId = sessionId;
            _eventHandler = _eventHandlerFactory.createHandler(sessionId);
        }

        @Override
        public void didConnect(ITcpReplyMessageHandler replyMessageHandler, SocketAddress socketAddress) {
            _eventHandler.didConnect(replyMessageHandler, socketAddress);
        }

        @Override
        public void didDisconnect() {
            _sessionMap.remove(_sessionId);

            _eventHandler.didDisconnect();
        }

        @Override
        public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, MessageList<? extends IByteBuff> messageList) {
            _eventHandler.didReceiveMessage(replyMessageHandler, messageList);
        }

        @Override
        public void didReceiveMessage(ITcpReplyMessageHandler replyMessageHandler, IByteBuff byteBuff) {
            _eventHandler.didReceiveMessage(replyMessageHandler, byteBuff);
        }
    }

}
