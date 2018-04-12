package com.beef.easytcp.asyncserver.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;

/**
 * Created by XingGu_Liu on 16/8/7.
 */
public class AsyncWriteEvent4File implements IAsyncWriteEvent {
	private final FileInputStream _fileIn;
	private final AsyncWriteEvent4FileChannel _writeEventForFileChannel;
	
    public AsyncWriteEvent4File(File data) throws FileNotFoundException {
        this(data, 0, data.length());
    }

    public AsyncWriteEvent4File(File data, long position, long byteLen) throws FileNotFoundException {
    	_fileIn = new FileInputStream(data);
    	
    	_writeEventForFileChannel = new AsyncWriteEvent4FileChannel(_fileIn.getChannel(), position, byteLen);
    }

	@Override
	public void close() throws IOException {
		try {
			_writeEventForFileChannel.close();
		} finally {
			_fileIn.close();
		}
	}

	@Override
	public boolean isClosed() {
		return _writeEventForFileChannel.isClosed();
	}

	@Override
	public boolean isWrittenDone() {
		return _writeEventForFileChannel.isWrittenDone();
	}

	@Override
	public void write(AsynchronousByteChannel targetChannel,
			CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler) {
		_writeEventForFileChannel.write(targetChannel, writeCompletionHandler);
	}


}
