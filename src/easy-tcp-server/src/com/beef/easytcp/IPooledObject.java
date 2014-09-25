package com.beef.easytcp;

public interface IPooledObject {
	
	public void setPoolReference(IPool<? extends IPooledObject> pool);
	
	public void returnToPool() throws Exception;
}
