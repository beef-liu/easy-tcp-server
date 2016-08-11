package com.beef.easytcp.asyncserver.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;

import org.apache.log4j.Logger;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.MessageList;

/**
 * Created by XingGu_Liu on 16/8/7.
 */
public class AsyncWriteEvent4MsgList implements IAsyncWriteEvent {
    private final static Logger logger = Logger.getLogger(AsyncWriteEvent4MsgList.class);

    private final MessageList<? extends IByteBuff> _data;
    private final ByteBuffer[] _buffers;

    private volatile int _curBufferIndex = 0;

    private volatile boolean _closed = false;

    public AsyncWriteEvent4MsgList(MessageList<? extends IByteBuff> data) {
        _data = data;
        _buffers = new ByteBuffer[data.size()];

        int i = 0;
        for(IByteBuff buff : data) {
            _buffers[i] = buff.getByteBuffer();

            i++;
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
        for (ByteBuffer buffer : _buffers) {
            if(buffer.hasRemaining()) {
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
        if(_curBufferIndex >= _buffers.length) {
            return;
        }

        ByteBuffer buffer = _buffers[_curBufferIndex];

        if(buffer.hasRemaining()) {
            targetChannel.write(buffer, this, writeCompletionHandler);
        } else {
            //move to next buffer
            _curBufferIndex++;
            write(targetChannel, writeCompletionHandler);
        }
    }

}
