package com.beef.easytcp.asyncserver.io;

import com.beef.easytcp.base.IByteBuff;
import com.beef.easytcp.base.handler.MessageList;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;

/**
 * Created by XingGu_Liu on 16/8/7.
 */
public class AsyncWriteEvent4ByteBuff implements IAsyncWriteEvent {

    private final IByteBuff _data;
    private volatile boolean _closed = false;

    public AsyncWriteEvent4ByteBuff(IByteBuff data) {
        _data = data;
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    @Override
    public void close() throws IOException {
        _closed = true;
        _data.destroy();
    }

    @Override
    public boolean isWrittenDone() {
        return !_data.getByteBuffer().hasRemaining();
    }

    @Override
    public void write(
            AsynchronousByteChannel targetChannel, CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler
    ) {
        final ByteBuffer buffer = _data.getByteBuffer();

        if(buffer.hasRemaining()) {
            targetChannel.write(buffer, this, writeCompletionHandler);
        }
    }

}
