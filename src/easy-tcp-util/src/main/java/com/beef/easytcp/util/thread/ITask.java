package com.beef.easytcp.util.thread;

public interface ITask extends Runnable {
	/*
	public static enum ErrorOccurredInMethod {
		BeforeRun,
		Run,
		Destroy
	};
	*/
	
	/**
	 * Tasks of same group will be executed in same single thread, and it keeps sequential.
	 * @return
	 */
	public int getTaskGroupId();
	
	/**
	 * supposed to write error log or something
	 * @param e
	 * @param methodType 0:before run
	 */
	//public void errorOccur(Throwable e, ErrorOccurredInMethod inMethod);
	
	/**
	 * beforeRun() will be invoked just before run()
	 */
	//public void beforeRun();

	/**
	 * destroy() will be invoked right after run()
	 */
	public void destroy();
	
}
