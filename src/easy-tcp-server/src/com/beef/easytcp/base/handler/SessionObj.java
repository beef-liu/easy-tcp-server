package com.beef.easytcp.base.handler;

public class SessionObj {
	protected int _id;
	protected Object _userObj = null;

	public int getId() {
		return _id;
	}
	
	public SessionObj(int id) {
		_id = id;
	}

	public Object getUserObj() {
		return _userObj;
	}

	public void setUserObj(Object userObj) {
		_userObj = userObj;
	}

}
