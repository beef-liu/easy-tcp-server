package com.beef.easytcp.asyncserver.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;

/**
 * Created by XingGu_Liu on 16/8/7.
 */
public class AsyncWriteEvent4File extends AsyncWriteEvent4FileChannel {

    public AsyncWriteEvent4File(File data, long position, long byteLen) throws FileNotFoundException {
        super((new FileInputStream(data)).getChannel(), position, byteLen);
    }

    @Override
    public void close() throws IOException {
        super.close();

        _data.close();
    }

}
