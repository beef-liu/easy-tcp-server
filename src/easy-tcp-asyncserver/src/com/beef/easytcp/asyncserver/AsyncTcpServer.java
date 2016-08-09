package com.beef.easytcp.asyncserver;

import com.beef.easytcp.asyncserver.handler.AsyncTcpSession;
import com.beef.easytcp.asyncserver.handler.IAsyncSession;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.asyncserver.io.IAsyncWriteEvent;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.buffer.ByteBufferPool;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpEventHandlerFactory;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.server.IServer;
import com.beef.easytcp.server.TcpServerConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncTcpServer implements IServer {
    private final static Logger logger = Logger.getLogger(AsyncTcpServer.class);

    protected TcpServerConfig _tcpServerConfig;

    protected boolean _isAllocateDirect = false;

    protected boolean _isBufferPoolAssigned = false;
    protected IByteBuffProvider _byteBuffProvider = null;
    protected ITcpEventHandlerFactory _eventHandlerFactory;

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
        _tcpServerConfig = tcpServerConfig;

        //if use ByteBuffer.allocateDirect(), then there is no backing array which means ByteBuffer.array() is null.
        _isAllocateDirect = isAllocateDirect;

        _eventHandlerFactory = eventHandlerFactory;
    }

    public AsyncTcpServer(
            TcpServerConfig tcpServerConfig,
            boolean isAllocateDirect,
            ITcpEventHandlerFactory eventHandlerFactory,
            IByteBuffProvider byteBuffProvider
    ) {
        this(tcpServerConfig, isAllocateDirect, eventHandlerFactory);

        _byteBuffProvider = byteBuffProvider;
        _isBufferPoolAssigned = true;
    }

    @Override
    public void start() {
        logger.info("AsyncTcpServer start >>>>>");

        try {
            startTcpServer();
        } catch(Throwable e) {
            logger.error("start()", e);
        }

        logger.info("AsyncTcpServer start done <<<<<");
    }

    @Override
    public void shutdown() {
        logger.info("AsyncTcpServer shutdow >>>>>");

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
        } catch (Throwable e) {
            logger.error(null, e);
        }

        try {
            _byteBuffProvider.close();
        } catch (Throwable e) {
            logger.error(null, e);
        }

        logger.info("AsyncTcpServer shutdown done <<<<<");
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
        int totalThreadCount = _tcpServerConfig.getSocketIOThreadCount()
                + _tcpServerConfig.getReadEventThreadCount()
                + _tcpServerConfig.getWriteEventThreadCount()
                ;
        _channelGroup = AsynchronousChannelGroup.withThreadPool(
                Executors.newFixedThreadPool(_tcpServerConfig.getConnectMaxCount())
        );

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
        if(!_isBufferPoolAssigned) {
            //init bytebuffer pool -----------------------------
            int bufferByteSize = _tcpServerConfig.getSocketReceiveBufferSize();
//			ByteBufferPoolFactory byteBufferPoolFactory = new ByteBufferPoolFactory(
//					_isAllocateDirect, bufferByteSize);

            GenericObjectPoolConfig byteBufferPoolConfig = new GenericObjectPoolConfig();
            byteBufferPoolConfig.setMaxIdle(_tcpServerConfig.getConnectMaxCount());
			/* old version
			byteBufferPoolConfig.setMaxActive(_PoolMaxActive);
			byteBufferPoolConfig.setMaxWait(_PoolMaxWait);
			*/
            byteBufferPoolConfig.setMaxTotal(_tcpServerConfig.getConnectMaxCount() * 2);
            byteBufferPoolConfig.setMaxWaitMillis(1000);

            //byteBufferPoolConfig.setSoftMinEvictableIdleTimeMillis(_softMinEvictableIdleTimeMillis);
            //byteBufferPoolConfig.setTestOnBorrow(_testOnBorrow);

            final ByteBufferPool bufferPool = new ByteBufferPool(
                    byteBufferPoolConfig, _isAllocateDirect, bufferByteSize);
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
                    logger.error("ServerSocket accept failed", e);
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
