package com.beef.easytcp.asyncserver.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;

/**
 * Created by beef_in_jp on 16/8/7.
 */
public class AsyncWriteEvent4FileChannel implements IAsyncWriteEvent {
    protected final FileChannel _data;

    private final ByteBuffer _buffer = ByteBuffer.allocate(512);
    
    private volatile long _position;
    private volatile long _remainder;

    private volatile boolean _closed = false;

    public AsyncWriteEvent4FileChannel(FileChannel data, long position, long byteLen) {
        _data = data;
        _position = position;

        _remainder = byteLen;
        
        _buffer.clear();
        _buffer.limit(0);
    }

    @Override
    public void close() throws IOException {
        _closed = true;
    	_data.close();
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    @Override
    public boolean isWrittenDone() {
        return (!_buffer.hasRemaining() && _remainder <= 0);
    }

    @Override
    public void write(
            AsynchronousByteChannel targetChannel,
            CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler
    ) {
        try {
        	if(_buffer.hasRemaining()) {
            	targetChannel.write(_buffer, this, writeCompletionHandler);
        	} else {
            	_buffer.clear();
            	_data.read(_buffer);
            	
            	_buffer.flip();
            	_remainder -= _buffer.remaining();
            	
            	targetChannel.write(_buffer, this, writeCompletionHandler);
        	}
        } catch (Throwable e) {
            writeCompletionHandler.failed(e, this);
        }
    }

}
