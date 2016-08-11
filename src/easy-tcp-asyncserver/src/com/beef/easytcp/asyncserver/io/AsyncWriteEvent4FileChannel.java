package com.beef.easytcp.asyncserver.io;

import java.io.IOException;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;

/**
 * Created by beef_in_jp on 16/8/7.
 */
public class AsyncWriteEvent4FileChannel implements IAsyncWriteEvent {
    protected final FileChannel _data;

    private volatile long _position;
    private volatile long _remainder;

    private volatile boolean _closed = false;

    public AsyncWriteEvent4FileChannel(FileChannel data, long position, long byteLen) {
        _data = data;
        _position = position;

        _remainder = byteLen;
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
        return (_remainder <= 0);
    }

    @Override
    public void write(
            AsynchronousByteChannel targetChannel,
            CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler
    ) {
        try {
            long written = _data.transferTo(
                    _position, _remainder,
                    Channels.newChannel(Channels.newOutputStream(targetChannel))
                    );

            _remainder -= written;

            writeCompletionHandler.completed((int)written, this);
        } catch (Throwable e) {
            writeCompletionHandler.failed(e, this);
        }
    }

}
