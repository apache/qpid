package org.apache.qpid.nclient.impl;

import java.util.UUID;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.amqp.AMQPMessage;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidMessageProducer;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.message.AMQPApplicationMessage;
import org.apache.qpid.nclient.message.MessageHeaders;

public class QpidMessageProducerImpl extends AbstractResource implements QpidMessageProducer
{
	private QpidSessionImpl _session;
	private AMQPMessage _amqpMessage;
	
	protected QpidMessageProducerImpl(QpidSessionImpl session) throws QpidException
	{
		super("Message Producer");
		_session = session;		
		_amqpMessage = session.getMessageHelper().getMessageClass();
	}

	/**
	 * -----------------------------------------------------
	 * Methods introduced by QpidMessageProducer
	 * -----------------------------------------------------
	 */
	public void send(boolean disableMessageId,boolean inline,AMQPApplicationMessage msg)throws QpidException
	{
		checkClosed();
		// need to handle the inline and reference case
		final MessageTransferBody messageTransferBody = prepareTransfer(disableMessageId,msg);
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_amqpMessage.transfer(messageTransferBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("message transfer failed due to");
		
		template.evaulateResponse(cb);
	}

	/**
	 * -----------------------------------------------------
	 * Methods introduced by AbstractResource
	 * -----------------------------------------------------
	 */
	protected void openResource() throws AMQPException, QpidException
	{
		
	}
	
	protected void closeResource() throws AMQPException, QpidException
	{
		
	}
	
	/**
	 * -----------------------------------------------------
	 * Helper Methods
	 * -----------------------------------------------------
	 */
	private MessageTransferBody prepareTransfer(boolean disableMessageId,AMQPApplicationMessage msg)
	{
		MessageHeaders msgHeaders = msg.getMessageHeaders();
		MessageTransferBody messageTransferBody = MessageTransferBody.createMethodBody(
				_session.getMajor(), 
				_session.getMinor(),
				msgHeaders.getAppId(), //appId
				msgHeaders.getApplicationHeaders(), //applicationHeaders
				new Content(Content.TypeEnum.INLINE_T,msg.getContentsAsBytes() ), //body
				msgHeaders.getContentType(), //contentEncoding, 
				msgHeaders.getContentType(), //contentType
				msgHeaders.getCorrelationId(), //correlationId
				msgHeaders.getDeliveryMode(), //deliveryMode non persistant
				new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_NAME),// destination
				new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_NAME),// exchange
				msgHeaders.getExpiration(), //expiration
				msgHeaders.isImmediate(), //immediate
				msgHeaders.isMandatory(), //mandatory
				(disableMessageId)?null : new AMQShortString(UUID.randomUUID().toString()), //messageId
				msgHeaders.getPriority(), //priority
				msg.getRedeliveredFlag(), //redelivered
				msgHeaders.getReplyTo(), //replyTo
				msgHeaders.getRoutingKey(), //routingKey, 
				"abc".getBytes(), //securityToken
				_session.getAccessTicket(), //ticket
				System.currentTimeMillis(), //timestamp
				msgHeaders.getTransactionId(), //transactionId
				msgHeaders.getTtl(), //ttl, 
				msgHeaders.getUserId() //userId
				);
		
		return messageTransferBody;
	}
}
