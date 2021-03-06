package com.beef.easytcp.asyncserver.handler;

import java.io.Closeable;

import com.beef.easytcp.base.IByteBuff;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public interface IByteBuffProvider extends Closeable {

    IByteBuff createBuffer();

}
