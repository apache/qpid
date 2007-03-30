package org.apache.qpid.nclient.amqp;

import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPExchange
{

	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */
	public abstract void declare(ExchangeDeclareBody exchangeDeclareBody, AMQPCallBack cb) throws AMQPException;

	public abstract void delete(ExchangeDeleteBody exchangeDeleteBody, AMQPCallBack cb) throws AMQPException;

}