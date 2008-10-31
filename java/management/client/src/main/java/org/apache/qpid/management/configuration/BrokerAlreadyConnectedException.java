package org.apache.qpid.management.configuration;

/**
 * Thrown when an attempt is made in order to connect QMan with an already connected broker.
 * 
 * @author Andrea Gazzarini
 */
public class BrokerAlreadyConnectedException extends Exception {

	private static final long serialVersionUID = -5082431738056504669L;

	private BrokerConnectionData _connectionData;
	
	/**
	 * Builds a new exception with the given data.
	 * 
	 * @param connectionData the broker connection data.
	 */
	public BrokerAlreadyConnectedException(BrokerConnectionData connectionData) {
		this._connectionData = connectionData;
	}

	/**
	 * Returns the connection data of the connected broker.
	 * 
	 * @return the connection data of the connected broker.
	 */
	public BrokerConnectionData getBrokerConnectionData()
	{
		return _connectionData;
	}
}
