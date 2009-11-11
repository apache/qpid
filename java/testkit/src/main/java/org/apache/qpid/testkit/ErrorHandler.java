package org.apache.qpid.testkit;

public interface ErrorHandler {

	public void handleError(String msg,Exception e);
}
