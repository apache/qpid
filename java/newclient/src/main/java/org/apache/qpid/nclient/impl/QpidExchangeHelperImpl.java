package org.apache.qpid.nclient.impl;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.nclient.amqp.AMQPExchange;
import org.apache.qpid.nclient.api.QpidExchangeHelper;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.core.AMQPException;

/**
 * The Qpid helper classes are written with JMS in mind.
 * Therefore most calls a blocking calls. However the low
 * level framework allows non blocking Async calls and should
 * use the low level API if u need asynchrony
 */
public class QpidExchangeHelperImpl extends AbstractResource implements QpidExchangeHelper
{
	private QpidSessionImpl _session;
	private AMQPExchange _exchange;
	
	protected QpidExchangeHelperImpl(QpidSessionImpl session)
	{
		super("Exchange Class");
		_session = session;		
	}

	/**
	 * -----------------------------------------------------
	 * Methods introduced by QpidExchangeHelper
	 * -----------------------------------------------------
	 */
	public void declareExchange(boolean autoDelete, boolean durable, String exchangeName,boolean internal, boolean nowait, boolean passive,String exchangeClass) throws QpidException
	{	
		final ExchangeDeclareBody exchangeDeclareBody = ExchangeDeclareBody.createMethodBody(
				_session.getMajor(), 
				_session.getMinor(), 
				null, // arguments
				autoDelete,
				durable, 
				new AMQShortString(exchangeName), 
				internal,
				nowait,
				passive,
				_session.getAccessTicket(),
				new AMQShortString(exchangeClass));
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_exchange.declare(exchangeDeclareBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Declare exchange failed due to");
		
		template.evaulateResponse(cb);
	}

	public void deleteExchange(String exchangeName, boolean ifUnused, boolean nowait) throws QpidException
	{
		final ExchangeDeleteBody exchangeDeclareBody = ExchangeDeleteBody.createMethodBody(
				_session.getMajor(), 
				_session.getMinor(),
				new AMQShortString(exchangeName), 
				ifUnused,
				nowait,
				_session.getAccessTicket());
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_exchange.delete(exchangeDeclareBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Delete exchange failed due to");
		
		template.evaulateResponse(cb);		
	}
	
	/**
	 * -----------------------------------------------------
	 * Methods introduced by AbstractResource
	 * -----------------------------------------------------
	 */
	protected void openResource() throws AMQPException
	{
		_exchange = _session.getClassFactory().createExchangeClass(_session.getChannel());
	}
	
	protected void closeResource() throws AMQPException
	{
		_session.getClassFactory().destoryExchangeClass(_session.getChannel(), _exchange);
	}
	
}
