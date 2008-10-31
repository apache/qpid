package org.apache.qpid.management.configuration;

/**
 * Thrown when a connection to a broker cannot be estabilished.
 * 
 * @author Andrea Gazzarini
 */
public class BrokerConnectionException extends Exception 
{
	private static final long serialVersionUID = 8170112238862494025L;

	/**
	 * Builds a new exception with the given cause.
	 * 
	 * @param cause the exception cause.
	 */
	BrokerConnectionException(Throwable cause) 
	{
		super(cause);
	}
}
