package org.apache.qpid.nclient.amqp;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPConnection
{

	/**
	 * Opens the TCP connection and let the formalities begin.
	 */
	public abstract ConnectionStartBody openTCPConnection() throws AMQPException;

	/**
	 * The current java broker implementation can send a connection tune body
	 * as a response to the startOk. Not sure if that is the correct behaviour.
	 */
	public abstract AMQMethodBody startOk(ConnectionStartOkBody connectionStartOkBody) throws AMQPException;

	/**
	 * The server will verify the response contained in the secureOK body and send a ConnectionTuneBody or it could
	 * issue a new challenge
	 */
	public abstract AMQMethodBody secureOk(ConnectionSecureOkBody connectionSecureOkBody) throws AMQPException;

	public abstract void tuneOk(ConnectionTuneOkBody connectionTuneOkBody) throws AMQPException;

	public abstract ConnectionOpenOkBody open(ConnectionOpenBody connectionOpenBody) throws AMQPException;

	public abstract ConnectionCloseOkBody close(ConnectionCloseBody connectioncloseBody) throws AMQPException;

}