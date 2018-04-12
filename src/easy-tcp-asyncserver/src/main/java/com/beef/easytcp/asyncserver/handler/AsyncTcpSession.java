package com.beef.easytcp.asyncserver.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.WritePendingException;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.*;

import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4ByteBuff;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4FileChannel;
import com.beef.easytcp.asyncserver.io.AsyncWriteEvent4MsgList;
import com.beef.easytcp.asyncserver.io.IAsyncWriteEvent;
import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.buffer.PooledByteBuffer;
import com.beef.easytcp.base.handler.ITcpEventHandler;
import com.beef.easytcp.base.handler.ITcpReplyMessageHandler;
import com.beef.easytcp.base.handler.MessageList;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public class AsyncTcpSession implements IAsyncSession {
    private final static Log logger = LogFactory.getLog(AsyncTcpSession.class);

    private final int _sessionId;
    private final AsynchronousSocketChannel _workChannel;
    private final ITcpEventHandler _eventHandler;
    private final IByteBuffProvider _byteBuffProvider;

    private final Queue<IAsyncWriteEvent> _writeEventQueue = new LinkedTransferQueue<IAsyncWriteEvent>();
    private final AtomicReference<IAsyncWriteEvent> _curWriteEvent = new AtomicReference<IAsyncWriteEvent>();

    //private boolean _didDispatchEventOfDidConnect = false;
    
    private final String _logMsgPrefix;
    
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

        //log message prefix
        String logMsgPrefix;
        try {
        	logMsgPrefix = 
            		((InetSocketAddress)_workChannel.getRemoteAddress()).getAddress().getHostAddress() 
            		+ ":" + ((InetSocketAddress)_workChannel.getRemoteAddress()).getPort()
            		+ "[" + _sessionId + "]"
            		;
        } catch (Throwable e) {
        	logger.error(null, e);
        	logMsgPrefix = "";
        }
        _logMsgPrefix = logMsgPrefix;

        try {
            _eventHandler.didConnect(
                    _replyMsgHandler,
                    _workChannel.getRemoteAddress()
            );
        } catch (Throwable e) {
            logger.error(_logMsgPrefix, e);
        }
    }

    @Override
    public void close() throws IOException {
        try { 
        	if(_workChannel.isOpen()) {
                _workChannel.close();

                _eventHandler.didDisconnect();
                
//                logger.debug(_logMsgPrefix + " tcp session closed");
        	}
        } catch (AsynchronousCloseException e) {
        	//channel already closed, do nothing
        } catch (Throwable e) {
            logger.error(_logMsgPrefix, e);
        }
    }

    @Override
    public void resumeReadLoop() {
    	/* move to constructor
    	if(!_didDispatchEventOfDidConnect) {
    		_didDispatchEventOfDidConnect = true;
            try {
                _eventHandler.didConnect(
                        _replyMsgHandler,
                        _workChannel.getRemoteAddress()
                );
            } catch (Throwable e) {
                logger.error(_logMsgPrefix, e);
            }
    	}
    	*/
    	
        IByteBuff buff = _byteBuffProvider.createBuffer();
//        logger.debug(_logMsgPrefix + "resumeReadLoop createBuffer:" + buff);
        try {
            buff.getByteBuffer().clear();
            _workChannel.read(
                    buff.getByteBuffer(),
                    buff,
                    _readCompletionHandler
            );
        } catch (Throwable e) {
            logger.error(_logMsgPrefix + " resumeReadLoop error --> session will be closed", e);

            try {
//                logger.debug(_logMsgPrefix + " buff destroyed in resumeReadLoop. buff:" + buff);
                
                buff.destroy();
            } catch (Throwable e2) {
                logger.error(_logMsgPrefix, e2);
            }

            try {
                close();
            } catch (Throwable e2) {
                logger.error(_logMsgPrefix, e2);
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
            	event = _writeEventQueue.poll();

                try {
                    event.write(_workChannel, _writeCompletionHandler);
                } catch (Throwable e) {
                    logger.error(_logMsgPrefix, e);

                    final Class<?> eCls = e.getClass();
                    if(eCls == WritePendingException.class) {
                        //do nothing, it is not supposed to occur because of _curWriteEvent.compareAndSet()
                        logger.error(_logMsgPrefix + " Unexpeced WritePendingException occurred !!!");
                    }

                    //close event
                    try {
                        event.close();
                    } catch (Throwable e2) {
                        logger.error(_logMsgPrefix, e2);
                    }

                    resumeWriteLoop();
                }
            }
        }
    }

    private void finishWriteEvent(IAsyncWriteEvent writeEvent) {
        if(_curWriteEvent.compareAndSet(writeEvent, null)) {
            try {
                //TODO:must delete after debuging
//                logger.debug(_logMsgPrefix + " buff destroyed in finishWriteEvent. buff:" + ((AsyncWriteEvent4ByteBuff)writeEvent).getData());
                
                writeEvent.close();
            } catch (Throwable e) {
                logger.error(_logMsgPrefix, e);
            }
        }
        
        //Thread.yield();
        resumeWriteLoop();
    }

    private CompletionHandler<Integer, IAsyncWriteEvent> _writeCompletionHandler = new CompletionHandler<Integer, IAsyncWriteEvent>() {
        @Override
        public void completed(Integer nBytes, IAsyncWriteEvent writeEvent) {
        	//logger.debug(_logMsgPrefix + " write completed. nBytes:" + nBytes);
        	
            if(writeEvent.isWrittenDone()) {
                //current event is done, fetch next event to write
                finishWriteEvent(writeEvent);
            } else {
                //continue to write data of current event
                //Thread.yield();
                writeEvent.write(_workChannel, this);
            }
        }

        @Override
        public void failed(Throwable e, IAsyncWriteEvent writeEvent) {
            logger.error(_logMsgPrefix, e);
            finishWriteEvent(writeEvent);
        }
    };

    private CompletionHandler<Integer, IByteBuff> _readCompletionHandler = new CompletionHandler<Integer, IByteBuff>() {
        @Override
        public void completed(Integer nBytes, IByteBuff buff) {
        	//logger.debug(_logMsgPrefix + " read completed. nBytes:" + nBytes);
        	
            //reading error
            if(nBytes < 0) {
                failed(new ClosedChannelException(), buff);
                return;
            }

            //dispatch received data to handler
            try {
                _eventHandler.didReceiveMessage(_replyMsgHandler, buff);
            } catch (Throwable e) {
                logger.error(_logMsgPrefix, e);
            } finally {
                if(PooledByteBuffer.class.isAssignableFrom(buff.getClass())
                        && ((PooledByteBuffer)buff).isDeferredDestroy()
                        ) {
                    //do not destroy, destroy should be invoked by whom invoked setDeferredDestroy()
                } else {
//                    logger.debug(_logMsgPrefix + " buff destroyed in _readCompletionHandler.completed(). buff:" + buff);
                    
                    buff.destroy();
                }
            }

            //consume reading loop
            //Thread.yield();
            resumeReadLoop();
        }

        @Override
        public void failed(Throwable exc, IByteBuff buff) {
        	Class<? extends Throwable> eCls = exc.getClass();
        	if(eCls == ClosedChannelException.class 
        			|| eCls == AsynchronousCloseException.class) {
        		logger.debug(_logMsgPrefix + " read failed -> channel already closed.");
        	} else {
                logger.error(_logMsgPrefix, exc);
        	}

            try {
//                logger.debug(_logMsgPrefix + " buff destroyed in _readCompletionHandler.failed(). buff:" + buff);
                
                //return IByteBuff to pool
                buff.destroy();
                
            } catch (Throwable e) {
                logger.error(_logMsgPrefix, e);
            }

            try {
                //close channel when reading error
                close();
            } catch (Throwable e) {
                logger.error(_logMsgPrefix, e);
            }
        }
    };

    private ITcpReplyMessageHandler _replyMsgHandler = new ITcpReplyMessageHandler() {
    	
        @Override
        public IByteBuff createBuffer() {
            IByteBuff buff = _byteBuffProvider.createBuffer();
//            logger.debug(_logMsgPrefix + " reply createBuffer:" + buff);
            return buff;
        }
        
        @Override
        public void disconnect() {
        	try {
            	AsyncTcpSession.this.close();
        	} catch (IOException e) {
        		logger.error("Error occurred in disconnect()", e);
        	}
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
