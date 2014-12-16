package com.beef.easytcp.util.thread;

public interface ITaskLoopThreadFactory <TaskType extends ITask> {
	public TaskLoopThread<TaskType> createThread();
}
