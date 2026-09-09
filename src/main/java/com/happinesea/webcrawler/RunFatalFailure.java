package com.happinesea.webcrawler;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

public final class RunFatalFailure {

	private RunFatalFailure() {
	}

	public static void propagateIfPresent(Throwable error) {
		Throwable fatal = find(error);
		if (fatal == null) {
			return;
		}
		if (fatal instanceof InterruptedException) {
			Thread.currentThread().interrupt();
		}
		if (error instanceof RuntimeException runtimeException) {
			throw runtimeException;
		}
		if (fatal instanceof RuntimeException runtimeException) {
			throw runtimeException;
		}
		throw new IllegalStateException("Run-fatal interrupted operation.", error);
	}

	private static Throwable find(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof InterruptedException
					|| current instanceof DataAccessException
					|| current instanceof TransactionException) {
				return current;
			}
		}
		return null;
	}
}
