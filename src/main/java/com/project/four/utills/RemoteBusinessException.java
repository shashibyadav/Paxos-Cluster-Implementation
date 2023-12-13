package com.project.four.utills;

import java.rmi.RemoteException;

public class RemoteBusinessException extends RemoteException {

	private static final long serialVersionUID = -4835766442555367407L;

	private String message = null;
	public RemoteBusinessException(String message) {
		super();
		this.message = message;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
}
