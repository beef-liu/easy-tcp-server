package com.beef.easytcp.asyncserver.handler;

import java.io.Closeable;

import com.beef.easytcp.asyncserver.io.IAsyncWriteEvent;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public interface IAsyncSession extends Closeable {

    void resumeReadLoop();

    void addWriteEvent(IAsyncWriteEvent writeEvent);

}
