package com.beef.easytcp;

public interface IPool <T extends IPooledObject> {
	
	public void returnObject(T obj);
	
	public T borrowObject();
	
}
