package org.apache.qpid.nclient.impl;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.nclient.amqp.AMQPQueue;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidQueueHelper;
import org.apache.qpid.nclient.core.AMQPException;

public class QpidQueueHelperImpl extends AbstractResource implements QpidQueueHelper
{
	private QpidSessionImpl _session;
	private AMQPQueue _queueClass;
	
	protected QpidQueueHelperImpl(QpidSessionImpl session)
	{
		super("Queue Class");
		_session = session;		
	}
	
	/**
	 * -----------------------------------------------------
	 * Methods introduced by QpidQueueHelper
	 * -----------------------------------------------------
	 */
	public void bindQueue(String exchangeName, boolean nowait, String queueName, String routingKey) throws QpidException
	{
		final QueueBindBody queueBindBody = QueueBindBody.createMethodBody(
				_session.getMajor(),
				_session.getMinor(), 
				null, //arguments
				new AMQShortString(exchangeName),//exchange
				nowait,
				new AMQShortString(queueName), //queue
				new AMQShortString(routingKey), //routingKey
				_session.getAccessTicket()
				);
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_queueClass.bind(queueBindBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Queue bind failed due to");
		
		template.evaulateResponse(cb);
	}

	public void declareQueue(boolean autoDelete, boolean durable, boolean exclusive, boolean nowait, boolean passive, String queueName)
			throws QpidException
	{
		final QueueDeclareBody queueDeclareBody = QueueDeclareBody.createMethodBody(
				_session.getMajor(),
				_session.getMinor(), 
				null, //arguments
				autoDelete,
				durable,
				exclusive,
				nowait,
				passive,
				new AMQShortString(queueName), //queue
				_session.getAccessTicket()
				);
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_queueClass.declare(queueDeclareBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Queue declare failed due to");
		
		template.evaulateResponse(cb);
	}

	public void deleteQueue(boolean ifEmpty, boolean ifUnused, boolean nowait, String queueName) throws QpidException
	{
		final QueueDeleteBody queueDeleteBody = QueueDeleteBody.createMethodBody(
				_session.getMajor(),
				_session.getMinor(), 
				ifEmpty,
				ifUnused,
				nowait,
				new AMQShortString(queueName), //queue
				_session.getAccessTicket()
				);
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_queueClass.delete(queueDeleteBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Queue delete failed due to");
		
		template.evaulateResponse(cb);
	}

	public void purgeQueue(boolean nowait, String queueName) throws QpidException
	{
		final QueuePurgeBody queuePurgeBody = QueuePurgeBody.createMethodBody(
				_session.getMajor(),
				_session.getMinor(), 
				nowait,
				new AMQShortString(queueName), //queue
				_session.getAccessTicket()
				);
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_queueClass.purge(queuePurgeBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Queue purge failed due to");
		
		template.evaulateResponse(cb);
	}

	public void unbindQueue(String exchangeName, String queueName, String routingKey) throws QpidException
	{
		final QueueUnbindBody queueUnbindBody = QueueUnbindBody.createMethodBody(
				_session.getMajor(),
				_session.getMinor(), 
				null,
				new AMQShortString(exchangeName),
				new AMQShortString(queueName), //queue
				new AMQShortString(routingKey),
				_session.getAccessTicket()
				);
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_queueClass.unbind(queueUnbindBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Queue unbind failed due to");
		
		template.evaulateResponse(cb);
	}
	
	
	/**
	 * -----------------------------------------------------
	 * Methods introduced by AbstractResource
	 * -----------------------------------------------------
	 */
	protected void openResource() throws AMQPException
	{
		_queueClass = _session.getClassFactory().createQueueClass(_session.getChannel());
	}
	
	protected void closeResource() throws AMQPException
	{
		_session.getClassFactory().destroyQueueClass(_session.getChannel(), _queueClass);
	}
}
