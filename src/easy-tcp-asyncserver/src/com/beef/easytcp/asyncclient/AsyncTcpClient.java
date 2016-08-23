package com.beef.easytcp.asyncclient;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import com.beef.easytcp.asyncserver.handler.AsyncTcpSession;
import com.beef.easytcp.asyncserver.handler.IByteBuffProvider;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuff;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuffer;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4File;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4FileChannel;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4MsgList;
import com.beef.easytcp.asyncserver.io.IAsyncWriteEvent;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.MessageList;
import com.beef.easytcp.client.ITcpClient;
import com.beef.easytcp.client.TcpClientConfig;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class AsyncTcpClient implements ITcpClient {
    private final static Logger logger = Logger.getLogger(AsyncTcpClient.class);

    protected final static AtomicInteger _sessionIdSeed = new AtomicInteger(0);

    
    private final TcpClientConfig _config;
    private final IByteBuffProvider _byteBuffProvider;
    private AsynchronousChannelGroup _channelGroup;

    private AsynchronousSocketChannel _socketChannel = null;
    private AsyncTcpSession _session;

    private ITcpEventHandler _eventHandler;

    private CountDownLatch _connectLatch = null;
    private final ReentrantLock _connectLock = new ReentrantLock();
    private volatile boolean _autoConnect = true;

    public void setEventHandler(ITcpEventHandler eventHandler) {
        _eventHandler = eventHandler;
    }

    public ITcpEventHandler getEventHandler() {
        return _eventHandler;
    }

    public boolean isAutoConnect() {
		return _autoConnect;
	}
    
    public void setAutoConnect(boolean autoConnect) {
		_autoConnect = autoConnect;
	}

    public AsyncTcpClient(
            TcpClientConfig config,
            IByteBuffProvider byteBuffProvider
    ) throws IOException {
        this(config, byteBuffProvider, null);
    }

    public AsyncTcpClient(
            TcpClientConfig config,
            IByteBuffProvider byteBuffProvider,
            AsynchronousChannelGroup channelGroup
    ) throws IOException {
        _config = config;

        _byteBuffProvider = byteBuffProvider;
        _channelGroup = channelGroup;
    }

    public void syncConnect() throws IOException {
    	if(isConnected()) {
    		//already connected
    		//logger.debug("syncConnect() already connected");
    		return;
    	}

    	_connectLock.lock();
    	try {
        	if(isConnected()) {
        		//already connected
        		//logger.debug("syncConnect() already connected");
        		return;
        	}
    		
    		_connectLatch = new CountDownLatch(1);
    		
    		//do connect
            connect();
    		
            try {
                _connectLatch.await(_config.getConnectTimeoutMS(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //do nothing
            }
    	} finally {
    		_connectLock.unlock();
    	}
    }

    @Override
    public void connect() throws IOException {
        _socketChannel = AsynchronousSocketChannel.open(_channelGroup);

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
        return _socketChannel != null && _socketChannel.isOpen();
    }

    public void send(IAsyncWriteEvent writeEvent) throws IOException {
    	if(_autoConnect) {
    		syncConnect();
    	}
    	
        _session.addWriteEvent(writeEvent);
    }

    public void send(IByteBuff msg) throws IOException {
    	if(_autoConnect) {
    		syncConnect();
    	}
    	
        _session.addWriteEvent(new AsyncWriteEvent4ByteBuff(msg));
    }

    public void send(MessageList<? extends IByteBuff> msgs) throws IOException {
    	if(_autoConnect) {
    		syncConnect();
    	}
    	
        _session.addWriteEvent(new AsyncWriteEvent4MsgList(msgs));
    }

    public void send(FileChannel fileChannel, long position, long byteLen) throws IOException {
    	if(_autoConnect) {
    		syncConnect();
    	}
    	
        _session.addWriteEvent(new AsyncWriteEvent4FileChannel(fileChannel, position, byteLen));
    }

    public void send(File file) throws IOException {
    	if(_autoConnect) {
    		syncConnect();
    	}
    	
        _session.addWriteEvent(new AsyncWriteEvent4File(file));
    }

    public void send(ByteBuffer byteBuffer) throws IOException {
    	if(_autoConnect) {
    		syncConnect();
    	}
    	
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

            if(_connectLatch != null) {
            	_connectLatch.countDown();
            }
        }

        @Override
        public void failed(Throwable e, Object attachment) {
            logger.error(null, e);
        }
    };

}
