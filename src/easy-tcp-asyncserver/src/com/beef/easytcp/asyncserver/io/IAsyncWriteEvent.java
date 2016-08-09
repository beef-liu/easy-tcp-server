package com.beef.easytcp.asyncserver.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;

/**
 * Created by XingGu_Liu on 16/8/8.
 */
public interface IAsyncWriteEvent extends Closeable {

    boolean isClosed();

    boolean isWrittenDone();

    void write(
            AsynchronousByteChannel targetChannel,
            CompletionHandler<Integer, IAsyncWriteEvent> writeCompletionHandler
    );

}
