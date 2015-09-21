package com.dotcms.staticpublish;

public class StaticPublishException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	final String message;

	public StaticPublishException(String message) {
		this.message = message;
	}
	public StaticPublishException(Exception e) {
		this.message = e.getMessage();
	}

	@Override
	public String getMessage() {
		// TODO Auto-generated method stub
		return message;
	}

	@Override
	public String getLocalizedMessage() {
		// TODO Auto-generated method stub
		return message;
	}

}
