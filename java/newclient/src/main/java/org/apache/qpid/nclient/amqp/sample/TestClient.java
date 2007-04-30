/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.nclient.amqp.sample;

import java.util.StringTokenizer;
import java.util.UUID;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueuePurgeOkBody;
import org.apache.qpid.nclient.amqp.AMQPCallBack;
import org.apache.qpid.nclient.amqp.AMQPChannel;
import org.apache.qpid.nclient.amqp.AMQPClassFactory;
import org.apache.qpid.nclient.amqp.AMQPConnection;
import org.apache.qpid.nclient.amqp.AMQPExchange;
import org.apache.qpid.nclient.amqp.AMQPMessage;
import org.apache.qpid.nclient.amqp.AMQPQueue;
import org.apache.qpid.nclient.amqp.AbstractAMQPClassFactory;
import org.apache.qpid.nclient.amqp.state.AMQPStateType;
import org.apache.qpid.nclient.transport.AMQPConnectionURL;
import org.apache.qpid.nclient.transport.ConnectionURL;
import org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType;

/**
 * This class illustrates the usage of the API
 * Notes this is just a simple demo.
 * 
 * I have used Helper classes to keep the code cleaner.
 * Will break this into unit tests later on
 */

@SuppressWarnings("unused")
public class TestClient
{
	private byte _major;

	private byte _minor;

	private ConnectionURL _url;

	private static int _channel = 2;

	// Need a Class factory per connection
	private AMQPClassFactory _classFactory;

	private int _ticket;

	public AMQPConnection openConnection() throws Exception
	{
		_classFactory = AbstractAMQPClassFactory.getFactoryInstance();
		
		//_url = new AMQPConnectionURL("amqp://guest:guest@test/localhost?brokerlist='vm://:3'");

		_url = new AMQPConnectionURL("amqp://guest:guest@test/test?brokerlist='tcp://localhost:5672?'");
		return _classFactory.createConnectionClass(_url, ConnectionType.TCP);
	}

	public void handleConnectionNegotiation(AMQPConnection con) throws Exception
	{
		StateHelper stateHelper = new StateHelper();
		_classFactory.getStateManager().addListener(AMQPStateType.CONNECTION_STATE, stateHelper);
		_classFactory.getStateManager().addListener(AMQPStateType.CHANNEL_STATE, stateHelper);

		//ConnectionStartBody
		ConnectionStartBody connectionStartBody = con.openTCPConnection();
		_major = connectionStartBody.getMajor();
		_minor = connectionStartBody.getMinor();

		FieldTable clientProperties = FieldTableFactory.newFieldTable();
		clientProperties.put(new AMQShortString(ClientProperties.instance.toString()), "Test"); // setting only the client id

		final String locales = new String(connectionStartBody.getLocales(), "utf8");
		final StringTokenizer tokenizer = new StringTokenizer(locales, " ");

		final String mechanism = SecurityHelper.chooseMechanism(connectionStartBody.getMechanisms());

		SaslClient sc = Sasl.createSaslClient(new String[]
		{ mechanism }, null, "AMQP", "localhost", null, SecurityHelper.createCallbackHandler(mechanism, _url));

		ConnectionStartOkBody connectionStartOkBody = ConnectionStartOkBody.createMethodBody(_major, _minor, clientProperties, new AMQShortString(
				tokenizer.nextToken()), new AMQShortString(mechanism), (sc.hasInitialResponse() ? sc.evaluateChallenge(new byte[0]) : null));
		// ConnectionSecureBody 
		AMQMethodBody body = con.startOk(connectionStartOkBody);
		ConnectionTuneBody connectionTuneBody;

		if (body instanceof ConnectionSecureBody)
		{
			ConnectionSecureBody connectionSecureBody = (ConnectionSecureBody) body;
			ConnectionSecureOkBody connectionSecureOkBody = ConnectionSecureOkBody.createMethodBody(_major, _minor, sc
					.evaluateChallenge(connectionSecureBody.getChallenge()));
			//Assuming the server is not going to send another challenge
			connectionTuneBody = (ConnectionTuneBody) con.secureOk(connectionSecureOkBody);

		}
		else
		{
			connectionTuneBody = (ConnectionTuneBody) body;
		}

		// Using broker supplied values
		ConnectionTuneOkBody connectionTuneOkBody = ConnectionTuneOkBody.createMethodBody(_major, _minor, connectionTuneBody.getChannelMax(),
				connectionTuneBody.getFrameMax(), connectionTuneBody.getHeartbeat());
		con.tuneOk(connectionTuneOkBody);

		ConnectionOpenBody connectionOpenBody = ConnectionOpenBody.createMethodBody(_major, _minor, null, true, new AMQShortString(_url
				.getVirtualHost()));

		ConnectionOpenOkBody connectionOpenOkBody = con.open(connectionOpenBody);
	}

	public void handleChannelNegotiation() throws Exception
	{
		AMQPChannel channel = _classFactory.createChannelClass(_channel);

		ChannelOpenBody channelOpenBody = ChannelOpenBody.createMethodBody(_major, _minor, new AMQShortString("myChannel1"));
		ChannelOpenOkBody channelOpenOkBody = channel.open(channelOpenBody);

		//lets have some fun
		ChannelFlowBody channelFlowBody = ChannelFlowBody.createMethodBody(_major, _minor, false);

		ChannelFlowOkBody channelFlowOkBody = channel.flow(channelFlowBody);
		System.out.println("Channel is " + (channelFlowOkBody.getActive() ? "active" : "suspend"));

		channelFlowBody = ChannelFlowBody.createMethodBody(_major, _minor, true);
		channelFlowOkBody = channel.flow(channelFlowBody);
		System.out.println("Channel is " + (channelFlowOkBody.getActive() ? "active" : "suspend"));
	}

	public void createExchange() throws Exception
	{
		AMQPExchange exchange = _classFactory.createExchangeClass(_channel);

		ExchangeDeclareBody exchangeDeclareBody = ExchangeDeclareBody.createMethodBody(_major, _minor, null, // arguments
				false,//auto delete
				false,// durable 
				new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_NAME), true, //internal
				false,// nowait
				false,// passive
				_ticket, new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_CLASS));

		AMQPCallBack cb = createCallBackWithMessage("Broker has created the exchange");
		exchange.declare(exchangeDeclareBody, cb);
		// Blocking for response
		while (!cb.isComplete())
		{
		}
	}

	public void createAndBindQueue() throws Exception
	{
		AMQPQueue queue = _classFactory.createQueueClass(_channel);

		QueueDeclareBody queueDeclareBody = QueueDeclareBody.createMethodBody(_major, _minor, null, //arguments
				false,//auto delete
				false,// durable
				false, //exclusive,
				false, //nowait, 
				false, //passive,
				new AMQShortString("MyTestQueue"), 0);

		AMQPCallBack cb = new AMQPCallBack()
		{

			@Override
			public void brokerResponded(AMQMethodBody body)
			{
				QueueDeclareOkBody queueDeclareOkBody = (QueueDeclareOkBody) body;
				System.out.println("[Broker has created the queue, " + "message count " + queueDeclareOkBody.getMessageCount() + "consumer count "
						+ queueDeclareOkBody.getConsumerCount() + "]\n");
			}

			@Override
			public void brokerRespondedWithError(AMQException e)
			{
			}

		};

		queue.declare(queueDeclareBody, cb);
		//Blocking for response
		while (!cb.isComplete())
		{
		}

		QueueBindBody queueBindBody = QueueBindBody.createMethodBody(_major, _minor, null, //arguments
				new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_NAME),//exchange
				false, //nowait
				new AMQShortString("MyTestQueue"), //queue
				new AMQShortString("RH"), //routingKey
				0 //ticket
				);

		cb = createCallBackWithMessage("Broker has bound the queue");
		queue.bind(queueBindBody, cb);
		//Blocking for response
		while (!cb.isComplete())
		{
		}
	}

	public void purgeQueue() throws Exception
	{
		AMQPQueue queue = _classFactory.createQueueClass(_channel);

		QueuePurgeBody queuePurgeBody = QueuePurgeBody.createMethodBody(_major, _minor, false, //nowait
				new AMQShortString("MyTestQueue"), //queue
				0 //ticket
				);

		AMQPCallBack cb = new AMQPCallBack()
		{

			@Override
			public void brokerResponded(AMQMethodBody body)
			{
				QueuePurgeOkBody queuePurgeOkBody = (QueuePurgeOkBody) body;
				System.out.println("[Broker has purged the queue, message count " + queuePurgeOkBody.getMessageCount() + "]\n");
			}

			@Override
			public void brokerRespondedWithError(AMQException e)
			{
			}

		};

		queue.purge(queuePurgeBody, cb);
		//Blocking for response
		while (!cb.isComplete())
		{
		}

	}

	public void deleteQueue() throws Exception
	{
		AMQPQueue queue = _classFactory.createQueueClass(_channel);

		QueueDeleteBody queueDeleteBody = QueueDeleteBody.createMethodBody(_major, _minor, false, //ifEmpty
				false, //ifUnused
				false, //nowait
				new AMQShortString("MyTestQueue"), //queue
				0 //ticket
				);

		AMQPCallBack cb = new AMQPCallBack()
		{

			@Override
			public void brokerResponded(AMQMethodBody body)
			{
				QueueDeleteOkBody queueDeleteOkBody = (QueueDeleteOkBody) body;
				System.out.println("[Broker has deleted the queue, message count " + queueDeleteOkBody.getMessageCount() + "]\n");
			}

			@Override
			public void brokerRespondedWithError(AMQException e)
			{
			}

		};

		queue.delete(queueDeleteBody, cb);
		//Blocking for response
		while (!cb.isComplete())
		{
		}

	}

	public void publishAndSubscribe() throws Exception
	{
		AMQPMessage message = _classFactory.createMessageClass(_channel, new MessageHelper());
		MessageConsumeBody messageConsumeBody = MessageConsumeBody.createMethodBody(_major, _minor, new AMQShortString("myClient"),// destination
				false, //exclusive
				null, //filter
				false, //noAck,
				false, //noLocal, 
				new AMQShortString("MyTestQueue"), //queue
				0 //ticket
				);

		AMQPCallBack cb = createCallBackWithMessage("Broker has accepted the consume");
		message.consume(messageConsumeBody, cb);
		//Blocking for response
		while (!cb.isComplete())
		{
		}

		// Sending 5 messages serially
		for (int i = 0; i < 5; i++)
		{
			cb = createCallBackWithMessage("Broker has accepted msg " + i);
			message.transfer(createMessages("Test" + i), cb);
			while (!cb.isComplete())
			{
			}
		}

		MessageCancelBody messageCancelBody = MessageCancelBody.createMethodBody(_major, _minor, new AMQShortString("myClient"));

		AMQPCallBack cb2 = createCallBackWithMessage("Broker has accepted the consume cancel");
		message.cancel(messageCancelBody, cb2);

	}

	private MessageTransferBody createMessages(String content) throws Exception
	{
		FieldTable headers = FieldTableFactory.newFieldTable();
		headers.setAsciiString(new AMQShortString("Test"), System.currentTimeMillis() + "");

		MessageTransferBody messageTransferBody = MessageTransferBody.createMethodBody(_major, _minor, new AMQShortString("testApp"), //appId
				headers, //applicationHeaders
				new Content(Content.TypeEnum.INLINE_T, content.getBytes()), //body
				new AMQShortString(""), //contentEncoding, 
				new AMQShortString("text/plain"), //contentType
				new AMQShortString("testApp"), //correlationId
				(short) 1, //deliveryMode non persistant
				new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_NAME),// destination
				new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_NAME),// exchange
				0l, //expiration
				false, //immediate
				false, //mandatory
				new AMQShortString(UUID.randomUUID().toString()), //messageId
				(short) 0, //priority
				false, //redelivered
				new AMQShortString("RH"), //replyTo
				new AMQShortString("RH"), //routingKey, 
				"abc".getBytes(), //securityToken
				0, //ticket
				System.currentTimeMillis(), //timestamp
				new AMQShortString(""), //transactionId
				0l, //ttl, 
				new AMQShortString("Hello") //userId
				);

		return messageTransferBody;

	}

	public void publishAndGet() throws Exception
	{
		AMQPMessage message = _classFactory.createMessageClass(_channel, new MessageHelper());
		AMQPCallBack cb = createCallBackWithMessage("Broker has accepted msg 5");

		MessageGetBody messageGetBody = MessageGetBody.createMethodBody(_major, _minor, new AMQShortString("myClient"), false, //noAck
				new AMQShortString("MyTestQueue"), //queue
				0 //ticket
				);

		//AMQPMessage message = _classFactory.createMessage(_channel,new MessageHelper());
		message.transfer(createMessages("Test"), cb);
		while (!cb.isComplete())
		{
		}

		cb = createCallBackWithMessage("Broker has accepted get");
		message.get(messageGetBody, cb);
	}

	// Creates a gneric call back and prints the given message
	private AMQPCallBack createCallBackWithMessage(final String msg)
	{
		AMQPCallBack cb = new AMQPCallBack()
		{

			@Override
			public void brokerResponded(AMQMethodBody body)
			{
				System.out.println(msg);
			}

			@Override
			public void brokerRespondedWithError(AMQException e)
			{
			}

		};

		return cb;
	}

	public static void main(String[] args)
	{
		TestClient test = new TestClient();
		try
		{
			AMQPConnection con = test.openConnection();
			test.handleConnectionNegotiation(con);
			test.handleChannelNegotiation();
			test.createExchange();
			test.createAndBindQueue();
			test.publishAndSubscribe();
			test.purgeQueue();
			test.publishAndGet();
			test.deleteQueue();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

}
