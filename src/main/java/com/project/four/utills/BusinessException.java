package com.project.four.utills;

public class BusinessException extends Exception {

	private static final long serialVersionUID = -8376708738191594422L;
	private String message = null;
	public BusinessException(String message) {
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