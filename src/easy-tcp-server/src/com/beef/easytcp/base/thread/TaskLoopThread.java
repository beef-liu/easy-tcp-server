package com.beef.easytcp.base.thread;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

//import com.beef.easytcp.base.thread.ITask.ErrorOccurredInMethod;

public class TaskLoopThread <TaskType extends ITask> extends Thread {
	private final static Logger logger = Logger.getLogger(TaskLoopThread.class);
	
	protected volatile boolean _stopFlg = false;
	protected LinkedBlockingQueue<TaskType> _taskQueue = new LinkedBlockingQueue<TaskType>();
	
	/**
	 * Thread's state will become RUNNABLE
	 * @param task
	 */
	public void addTask(TaskType task) {
		_taskQueue.add(task);
	}

	/**
	 * Thread's state will become RUNNABLE
	 * @param taskList
	 */
	public void addTasks(Collection<TaskType> taskList) {
		_taskQueue.addAll(taskList);
	}
	
	/**
	 * Thread's state will become WAITING after clear queue (task.destroy() will be invoked) 
	 */
	public void clearWaitingTasks() {
		TaskType t;
		while((t = _taskQueue.poll()) != null) {
			try {
				t.destroy();
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
	}
	
	/**
	 * Thread's state will become TERMINATED after shutdown.
	 */
	public void shutdown() {
		_stopFlg = true;
		
		try {
			clearWaitingTasks();
		} catch(Throwable e) {
			logger.error(null, e);
		}
		
		try {
			this.interrupt();
		} catch(Throwable e) {
			logger.error(null, e);
		}
	}
	
	@Override
	public void run() {
		TaskType t;
		
		try {
			//_stopFlg is for circumstance that InterruptedException occurred and was catched in implementation method
			while(!_stopFlg) {
				t = _taskQueue.take();
				
				/*
				//do beforeRun() --------
				try {
					t.beforeRun();
				} catch(Throwable e) {
					try {
						t.errorOccur(e, ErrorOccurredInMethod.BeforeRun);
					} catch(Throwable e1) {
						logError(e1);
					}
				}
				*/
				
				//do run() --------
				try {
					t.run();
				} catch(Throwable e) {
					/*
					try {
						t.errorOccur(e, ErrorOccurredInMethod.Run);
					} catch(Throwable e1) {
						logError(e1);
					}
					*/
					logger.error(null, e);
				} finally {
					try {
						//destroy
						t.destroy();
					} catch(Throwable e) {
						/*
						try {
							t.errorOccur(e, ErrorOccurredInMethod.Destroy);
						} catch(Throwable e1) {
							logError(e1);
						}
						*/
						logger.error(null, e);
					}
				}
			}
		} catch(InterruptedException e) {
			//System.out.println("TaskLoop.run() end by interrupt");
		}
	}
		
}
