package com.happinesea.webcrawler;

public class NotFoundContentsException extends Exception {

	private static final long serialVersionUID = 8456736524587331222L;

//	public NotFoundContentsException() {
//		super();
//	}

	public NotFoundContentsException(String message) {
		super(message);
	}

//	public NotFoundContentsException(Throwable cause) {
//		super(cause);
//	}

	public NotFoundContentsException(String message, Throwable cause) {
		super(message, cause);
	}

//	public NotFoundContentsException(String message, Throwable cause, boolean enableSuppression,
//			boolean writableStackTrace) {
//		super(message, cause, enableSuppression, writableStackTrace);
//	}

}
