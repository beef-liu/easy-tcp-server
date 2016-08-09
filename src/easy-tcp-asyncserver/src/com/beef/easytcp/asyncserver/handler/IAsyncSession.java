package com.beef.easytcp.asyncserver.handler;

import com.beef.easytcp.asyncserver.io.IAsyncWriteEvent;

import java.io.Closeable;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public interface IAsyncSession extends Closeable {

    void resumeReadLoop();

    void addWriteEvent(IAsyncWriteEvent writeEvent);

}
