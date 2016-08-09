package com.beef.easytcp.asyncserver.handler;

import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuff;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4FileChannel;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4MsgList;
import com.beef.easytcp.asyncserver.io.IAsyncWriteEvent;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class AsyncTcpSession implements IAsyncSession {
    private final static Logger logger = Logger.getLogger(AsyncTcpSession.class);

    private final int _sessionId;
    private final AsynchronousSocketChannel _workChannel;
    private final ITcpEventHandler _eventHandler;
    private final IByteBuffProvider _byteBuffProvider;

    private final Queue<IAsyncWriteEvent> _writeEventQueue = new LinkedTransferQueue<IAsyncWriteEvent>();
    private final AtomicReference<IAsyncWriteEvent> _curWriteEvent = new AtomicReference<IAsyncWriteEvent>();

    private boolean _didDispatchEventOfDidConnect = false;
    
    public int getSessionId() {
        return _sessionId;
    }

    public AsyncTcpSession(
            AsynchronousSocketChannel workChannel,
            int sessionId,
            ITcpEventHandler eventHandler,
            IByteBuffProvider byteBuffProvider
    ) {
        _workChannel = workChannel;
        _sessionId = sessionId;
        _eventHandler = eventHandler;
        _byteBuffProvider = byteBuffProvider;
    }

    @Override
    public void close() throws IOException {
        try {
            _workChannel.close();
        } catch (Throwable e) {
            logger.error(null, e);
        }

        try {
            _eventHandler.didDisconnect();
        } catch (Throwable e) {
            logger.error(null, e);
        }
    }

    @Override
    public void resumeReadLoop() {
    	if(_didDispatchEventOfDidConnect) {
            try {
                _eventHandler.didConnect(
                        _replyMsgHandler,
                        _workChannel.getRemoteAddress()
                );
            } catch (Throwable e) {
                logger.error(null, e);
            }
    	}
    	
        IByteBuff buff = _byteBuffProvider.createBuffer();
        try {
            buff.getByteBuffer().clear();
            _workChannel.read(
                    buff.getByteBuffer(),
                    buff,
                    _readCompletionHandler
            );
        } catch (Throwable e) {
            logger.error("resumeReadLoop error --> session will be closed", e);

            try {
                buff.destroy();
            } catch (Throwable e2) {
                logger.error(null, e2);
            }

            try {
                close();
            } catch (Throwable e2) {
                logger.error(null, e2);
            }
        }
    }

    @Override
    public void addWriteEvent(IAsyncWriteEvent writeEvent) {
        _writeEventQueue.add(writeEvent);

        resumeWriteLoop();
    }

    private void resumeWriteLoop() {
        IAsyncWriteEvent event = _writeEventQueue.peek();
        if(event != null) {
            //set event to do only when current one is null which means current one is done
            if(_curWriteEvent.compareAndSet(null, event)) {
                _writeEventQueue.poll();

                try {
                    event.write(_workChannel, _writeCompletionHandler);
                } catch (Throwable e) {
                    logger.error(null, e);

                    final Class<?> eCls = e.getClass();
                    if(eCls == WritePendingException.class) {
                        //do nothing, it is not supposed to occur because of _curWriteEvent.compareAndSet()
                        logger.error("Unexpeced WritePendingException occurred !!!");
                    }

                    //close event
                    try {
                        event.close();
                    } catch (Throwable e2) {
                        logger.error(null, e2);
                    }

                    resumeWriteLoop();
                }
            }
        }
    }

    private void finishWriteEvent(IAsyncWriteEvent writeEvent) {
        try {
            writeEvent.close();
        } catch (Throwable e) {
            logger.error(null, e);
        }

        _curWriteEvent.compareAndSet(writeEvent, null);
        resumeWriteLoop();
    }

    private CompletionHandler<Integer, IAsyncWriteEvent> _writeCompletionHandler = new CompletionHandler<Integer, IAsyncWriteEvent>() {
        @Override
        public void completed(Integer nBytes, IAsyncWriteEvent writeEvent) {
            if(writeEvent.isWrittenDone()) {
                //current event is done, fetch next event to write
                finishWriteEvent(writeEvent);
            } else {
                //continue to write data of current event
                writeEvent.write(_workChannel, this);
            }
        }

        @Override
        public void failed(Throwable e, IAsyncWriteEvent writeEvent) {
            logger.error(null, e);
            finishWriteEvent(writeEvent);
        }
    };

    private CompletionHandler<Integer, IByteBuff> _readCompletionHandler = new CompletionHandler<Integer, IByteBuff>() {
        @Override
        public void completed(Integer nBytes, IByteBuff buff) {
            //reading error
            if(nBytes < 0) {
                failed(new ClosedChannelException(), buff);
                return;
            }

            //consume reading loop
            resumeReadLoop();

            //dispatch received data to handler
            try {
                _eventHandler.didReceiveMessage(_replyMsgHandler, buff);
            } catch (Throwable e) {
                logger.error(null, e);
            } finally {
                buff.destroy();
            }
        }

        @Override
        public void failed(Throwable exc, IByteBuff buff) {
            logger.error(null, exc);

            try {
                //return IByteBuff to pool
                buff.destroy();
            } catch (Throwable e) {
                logger.error(null, e);
            }

            try {
                //close channel when reading error
                close();
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }
    };

    private ITcpReplyMessageHandler _replyMsgHandler = new ITcpReplyMessageHandler() {
        @Override
        public IByteBuff createBuffer() {
            return _byteBuffProvider.createBuffer();
        }

        @Override
        public void sendMessage(IByteBuff msg) {
            addWriteEvent(new AsyncWriteEvent4ByteBuff(msg));
        }

        @Override
        public void sendMessage(MessageList<? extends IByteBuff> messageList) {
            addWriteEvent(new AsyncWriteEvent4MsgList(messageList));
        }

        @Override
        public void sendMessage(FileChannel fileChannel, long position, long byteLen) {
            addWriteEvent(new AsyncWriteEvent4FileChannel(fileChannel, position, byteLen));
        }

    };
}
