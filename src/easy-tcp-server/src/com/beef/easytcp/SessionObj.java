package com.beef.easytcp;

public class SessionObj {
	private int _id;
	private Object _obj = null;

	public int getId() {
		return _id;
	}
	
	public SessionObj(int id) {
		_id = id;
	}

	public Object getObj() {
		return _obj;
	}

	public void setObj(Object obj) {
		_obj = obj;
	}
	
}
