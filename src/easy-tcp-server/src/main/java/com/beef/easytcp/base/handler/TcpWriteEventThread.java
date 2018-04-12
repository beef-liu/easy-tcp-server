package com.beef.easytcp.base.handler;

import java.nio.channels.Selector;

import com.beef.easytcp.util.thread.TaskLoopThread;

public class TcpWriteEventThread extends TaskLoopThread<TcpWriteEvent> {
	protected Selector _writeSelector;
	
	public TcpWriteEventThread(Selector writeSelector) {
		_writeSelector = writeSelector;
	}
	
	public Selector getWriteSelector() {
		return _writeSelector;
	}
	
}
