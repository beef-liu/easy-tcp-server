package com.beef.easytcp.asyncserver.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.Iterator;

import org.apache.commons.logging.*;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.MessageList;

/**
 * Created by XingGu_Liu on 16/8/7.
 */
public class AsyncWriteEvent4MsgList implements IAsyncWriteEvent {
    private final static Log logger = LogFactory.getLog(AsyncWriteEvent4MsgList.class);

    private final MessageList<? extends IByteBuff> _data;
    private final ByteBuffer[] _bufferArray;

    private volatile boolean _closed = false;

    public AsyncWriteEvent4MsgList(MessageList<? extends IByteBuff> data) {
        _data = data;
        
        _bufferArray = new ByteBuffer[data.size()];
		Iterator<? extends IByteBuff> iterMsgs = data.iterator();
		int index = 0;
		while(iterMsgs.hasNext()) {
			try {
				_bufferArray[index++] = iterMsgs.next().getByteBuffer(); 
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
        
    }

    @Override
    public void close() throws IOException {
        _closed = true;
        for (IByteBuff buff : _data) {
            try {
                buff.destroy();
            } catch (Throwable e) {
                logger.error(null, e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    @Override
    public boolean isWrittenDone() {
        for(IByteBuff buff : _data) {
            if(buff.getByteBuffer().hasRemaining()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void write(
            AsynchronousByteChannel targetChannel,
            CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler
    ) {
    	/*
        for(IByteBuff buff : _data) {
            if(buff.getByteBuffer().hasRemaining()) {
                targetChannel.write(buff.getByteBuffer(), this, writeCompletionHandler);
                return;
            }
        }
        */
    	
    	for(ByteBuffer buffer : _bufferArray) {
    		if(buffer.hasRemaining()) {
                targetChannel.write(buffer, this, writeCompletionHandler);
                return;
    		}
    	}
        
        writeCompletionHandler.completed(0, this);
    }

}
