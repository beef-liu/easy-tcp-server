package com.beef.easytcp.asyncserver.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;

/**
 * Created by XingGu_Liu on 16/8/7.
 */
public class AsyncWriteEvent4ByteBuffer implements IAsyncWriteEvent {

    private final ByteBuffer _data;
    private volatile boolean _closed = false;

    public AsyncWriteEvent4ByteBuffer(ByteBuffer data) {
        _data = data;
    }

    @Override
    public void close() throws IOException {
        _closed = true;
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    @Override
    public boolean isWrittenDone() {
        return !_data.hasRemaining();
    }

    @Override
    public void write(
            AsynchronousByteChannel targetChannel,
            CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler
    ) {
        if(_data.hasRemaining()) {
            targetChannel.write(_data, this, writeCompletionHandler);
        }
    }
}
