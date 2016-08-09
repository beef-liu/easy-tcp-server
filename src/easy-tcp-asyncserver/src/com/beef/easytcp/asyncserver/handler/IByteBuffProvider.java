package com.beef.easytcp.asyncserver.handler;

import com.beef.easytcp.base.IByteBuff;

import java.io.Closeable;

/**
 * Created by XingGu_Liu on 16/8/9.
 */
public interface IByteBuffProvider extends Closeable {

    IByteBuff createBuffer();

}
