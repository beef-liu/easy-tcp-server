package com.beef.easytcp.base;

public interface IPool <T extends IPooledObject> {
	
	public void returnObject(T obj);
	
	public T borrowObject();
	
	public void close();
}
