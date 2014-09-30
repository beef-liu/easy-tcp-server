package com.beef.easytcp.base.thread;

public interface ITaskLoopThreadFactory <TaskType extends ITask> {
	public TaskLoopThread<TaskType> createThread();
}
