package com.beef.easytcp.server;

public class TcpException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4911937675831768347L;

	public TcpException() {
		super();
	}
	public TcpException(String errorMsg) {
		super(errorMsg);
	}
	
	public TcpException(Throwable e) {
		super(e);
	}
	
	public TcpException(String errorMsg, Throwable e) {
		super(errorMsg, e);
	}
	
}
