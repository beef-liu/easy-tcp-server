package com.beef.easytcp.junittest;

import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

public class TestThread {

	public void test1() {
		try {
			MyThread t = new MyThread();
			t.start();
			
			Thread.sleep(1000);
			t.suspendThread();
			
			System.out.println("111 --------------");

			Thread.sleep(5000);
			t.resumeThread();
			
			System.out.println("222 --------------");

			Thread.sleep(3000);
			
			
			/*
			int runCount = 0;
			while((runCount++) < 100) {
				System.out.println("t.isInterrupted():" + t.isInterrupted());
				
				Thread.sleep(500);
				t.interrupt();
				System.out.println("t.isInterrupted():" + t.isInterrupted());

				
				
				System.out.println("---------------------------------------");
				Thread.sleep(3000);
			}
			*/
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void test2() {
		try {
			TaskLoop<String> taskLoop = new TaskLoop<String>();
			
			System.out.println("taskLoop start ---------");
			
			taskLoop.start();
		
			Thread.sleep(100);
			System.out.println("10 taskLoop thread state:" + taskLoop.getState());
			
			Thread.sleep(3000);
			
			for(int i = 0; i < 10; i++) {
				taskLoop.addTask(String.valueOf(i));
			}

			System.out.println("20 taskLoop thread state:" + taskLoop.getState());
			Thread.sleep(5000);

			System.out.println("30 taskLoop thread state:" + taskLoop.getState());
			taskLoop.shutdown();
			
			
			Thread.sleep(1000);
			System.out.println("40 taskLoop thread state:" + taskLoop.getState());
			
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
	
	public void test3() {
		try {

			
			MyThread2 t = new MyThread2();
			MyThread2 t2 = new MyThread2();
			System.out.println("MyThread2 t.id:" + t.getId() + " t2.id:" + t2.getId());

			t.start();

			
			System.out.println("10 MyThread2 state:" + t.getState());
			Thread.sleep(1000);

			System.out.println("20 MyThread2 state:" + t.getState());
			t.interrupt();

			Thread.sleep(3000);
			System.out.println("30 MyThread2 state:" + t.getState());
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
	
	private static class MyThread extends Thread {
		private volatile boolean _stopFlg = false;
		private int _counter;
		
		private Object _waitObj = new Object();
		private volatile boolean _waitFlg = false;
		
		public void shutdown() {
			_stopFlg = true;
		}
		
		public void suspendThread() {
			_waitFlg = true;
		}
		
		public void resumeThread() {
			_waitFlg = false;
			synchronized (_waitObj) {
				_waitObj.notifyAll();
			}
		}
		
		@Override
		public void run() {
			while(true) {
				if(_waitFlg) {
					synchronized (_waitObj) {
						try {
							_waitObj.wait();
						} catch (InterruptedException e1) {
							System.out.println("wait interrupted-----");
						}
					}
				}
				
				System.out.println("_counter:" + (_counter++));
				
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}
	}
	
	protected static class MyThread2 extends Thread {
		private long _counter = 0;
		
		@Override
		public void run() {
			try {
				while(true) {
					System.out.println("_counter:" + (_counter++));
				}
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
	}
	
	protected static class TaskLoop <T> extends Thread {
		private volatile boolean _stopFlg = false;
		
		private LinkedBlockingQueue<T> _taskQueue = new LinkedBlockingQueue<T>();

		public void addTask(T t) {
			_taskQueue.add(t);
		}
		
		public void shutdown() {
			_stopFlg = true;
			this.interrupt();
		}
		
		public void clearTasks() {
			_taskQueue.clear();
		}
		
		@Override
		public void run() {
			T t;
			
			try {
				//_stopFlg is for circumstance that InterruptedException occurred and was catched in implementation method 
				while(!_stopFlg) {
					t = _taskQueue.take();
					
					System.out.println("TaskLoop.run() " + t);
					
					Thread.sleep(250);
				}
			} catch(InterruptedException e) {
				System.out.println("TaskLoop.run() end by interrupt");
			}
		}
		
	}
}
