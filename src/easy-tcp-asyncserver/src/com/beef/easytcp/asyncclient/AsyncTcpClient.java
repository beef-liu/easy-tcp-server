package com.beef.easytcp.asyncclient;

import com.beef.easytcp.asyncserver.handler.AsyncTcpSession;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.asyncserver.io.*;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.client.ITcpClient;
import com.beef.easytcp.client.TcpClientConfig;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class AsyncTcpClient implements ITcpClient {
    private final static Logger logger = Logger.getLogger(AsyncTcpClient.class);

    protected final static AtomicInteger _sessionIdSeed = new AtomicInteger(0);

    private final TcpClientConfig _config;
    private final IByteBuffProvider _byteBuffProvider;

    private AsynchronousSocketChannel _socketChannel;
    private AsyncTcpSession _session;

    private ITcpEventHandler _eventHandler;

    public void setEventHandler(ITcpEventHandler eventHandler) {
        _eventHandler = eventHandler;
    }

    public ITcpEventHandler getEventHandler() {
        return _eventHandler;
    }

    public AsyncTcpClient(
            TcpClientConfig config,
            IByteBuffProvider byteBuffProvider
    ) throws IOException {
        _config = config;
        _byteBuffProvider = byteBuffProvider;

        _socketChannel = AsynchronousSocketChannel.open(
                AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool())
        );
    }


    @Override
    public void connect() throws IOException {
        _socketChannel.setOption(
                StandardSocketOptions.SO_REUSEADDR, true
        );
        _socketChannel.setOption(
                StandardSocketOptions.TCP_NODELAY,
                _config.isTcpNoDelay()
        );
        _socketChannel.setOption(
                StandardSocketOptions.SO_RCVBUF,
                _config.getReceiveBufferSize()
        );
        _socketChannel.setOption(
                StandardSocketOptions.SO_SNDBUF,
                _config.getSendBufferSize()
        );
        _socketChannel.setOption(
                StandardSocketOptions.SO_KEEPALIVE,
                _config.isKeepAlive()
        );


        _socketChannel.connect(
                new InetSocketAddress(_config.getHost(), _config.getPort()),
                null,
                _connectCompletionHandler
        );
        logger.debug("Connecting to server ... --> " + _config.getHost() + ":" + _config.getPort());
    }

    @Override
    public void disconnect() throws IOException {
        _session.close();
    }


    @Override
    public boolean isConnected() {
        return _socketChannel.isOpen();
    }

    public void send(IAsyncWriteEvent writeEvent) {
        _session.addWriteEvent(writeEvent);
    }

    public void send(IByteBuff msg) {
        _session.addWriteEvent(new AsyncWriteEvent4ByteBuff(msg));
    }

    public void send(MessageList<? extends IByteBuff> msgs) {
        _session.addWriteEvent(new AsyncWriteEvent4MsgList(msgs));
    }

    public void send(FileChannel fileChannel, long position, long byteLen) {
        _session.addWriteEvent(new AsyncWriteEvent4FileChannel(fileChannel, position, byteLen));
    }

    public void send(File file) throws FileNotFoundException {
        _session.addWriteEvent(new AsyncWriteEvent4File(file));
    }

    public void send(ByteBuffer byteBuffer) {
        _session.addWriteEvent(new AsyncWriteEvent4ByteBuffer(byteBuffer));
    }

    private CompletionHandler<Void, Object> _connectCompletionHandler = new CompletionHandler<Void, Object>() {

        @Override
        public void completed(Void result, Object attachment) {
            logger.debug("Connected to server --> " + _config.getHost() + ":" + _config.getPort());

            _session = new AsyncTcpSession(
                    _socketChannel,
                    _sessionIdSeed.incrementAndGet(),
                    _eventHandler,
                    _byteBuffProvider
            );
            _session.resumeReadLoop();
        }

        @Override
        public void failed(Throwable e, Object attachment) {
            logger.error(null, e);
        }
    };

}
