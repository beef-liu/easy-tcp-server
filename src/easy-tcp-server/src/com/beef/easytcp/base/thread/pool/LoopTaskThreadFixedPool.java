package com.beef.easytcp.base.thread.pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import com.beef.easytcp.base.thread.ITask;
import com.beef.easytcp.base.thread.ITaskLoopThreadFactory;
import com.beef.easytcp.base.thread.TaskLoopThread;

public class LoopTaskThreadFixedPool <TaskType extends ITask> {
	private final static Logger logger = Logger.getLogger(LoopTaskThreadFixedPool.class);
	
	protected int _poolSize;
	protected int _bitAndMask;

	protected List<TaskLoopThread<TaskType>> _threadList = new ArrayList<TaskLoopThread<TaskType>>();
	protected ITaskLoopThreadFactory<TaskType> _threadFactory = new ITaskLoopThreadFactory<TaskType>() {

		@Override
		public TaskLoopThread<TaskType> createThread() {
			return new TaskLoopThread<TaskType>();
		}
		
	};
	
	public LoopTaskThreadFixedPool(int poolSize) {
		this(poolSize, null);
	}
	
	public LoopTaskThreadFixedPool(int poolSize, ITaskLoopThreadFactory<TaskType> threadFactory) {
		if(threadFactory != null) {
			_threadFactory = threadFactory;
		}

		int poolSizeMaskBitLen = (int) (Math.log(poolSize) / Math.log(2));
		if(poolSizeMaskBitLen < 0) {
			poolSizeMaskBitLen = 0;
		}
		
		_bitAndMask = ((int) Math.pow(2, poolSizeMaskBitLen) - 1);
		
		_poolSize = (int)Math.pow(2, poolSizeMaskBitLen);
		
		preCreateThreads();
	}
	
	public int getPoolSize() {
		return _poolSize;
	}
	
	public TaskLoopThread<TaskType> getThreadOfGroup(int groupId) {
		int threadIndex = groupId & _bitAndMask;
		
		return _threadList.get(threadIndex);
	}
	
	public Iterator<TaskLoopThread<TaskType>> getAllThreads() {
		return _threadList.iterator();
	}
	
	protected void preCreateThreads() {
		TaskLoopThread<TaskType> t;
		for(int i = 0; i < _poolSize; i++) {
			t = _threadFactory.createThread();
			
			t.start();

			_threadList.add(t);
		}
	}
	
	public void execute(TaskType task) {
		getThreadOfGroup(task.getTaskGroupId()).addTask(task);
	}
	
	public void shutdown() {
		for(int i = 0; i < _poolSize; i++) {
			try {
				_threadList.get(i).shutdown();
			} catch(Throwable e) {
				logger.error(null, e);
			}
		}
	}
	
}
