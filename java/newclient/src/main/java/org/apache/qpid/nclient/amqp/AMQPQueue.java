package org.apache.qpid.nclient.amqp;

import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPQueue
{

	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */
	public abstract void declare(QueueDeclareBody queueDeclareBody, AMQPCallBack cb) throws AMQPException;

	public abstract void bind(QueueBindBody queueBindBody, AMQPCallBack cb) throws AMQPException;

	// Queue.unbind doesn't have nowait
	public abstract void unbind(QueueUnbindBody queueUnbindBody, AMQPCallBack cb) throws AMQPException;

	public abstract void purge(QueuePurgeBody queuePurgeBody, AMQPCallBack cb) throws AMQPException;

	public abstract void delete(QueueDeleteBody queueDeleteBody, AMQPCallBack cb) throws AMQPException;

}