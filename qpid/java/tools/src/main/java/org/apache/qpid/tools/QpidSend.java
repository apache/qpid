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
package org.apache.qpid.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.tools.TestConfiguration.MessageType;
import org.apache.qpid.tools.report.BasicReporter;
import org.apache.qpid.tools.report.Reporter;
import org.apache.qpid.tools.report.Statistics.Throughput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidSend
{
	private Connection con;
	private Session session;
	private Destination dest;
	private MessageProducer producer;
	private MessageType msgType;
	private Message msg;
	private Object payload;
	private List<Object> payloads;
	private boolean cacheMsg = false;
	private boolean randomMsgSize = false;
	private boolean durable = false;
	private Random random;
	private int msgSizeRange = 1024;
	private int totalMsgCount = 0;
	private boolean rateLimitProducer = false;
	private boolean transacted = false;
	private int txSize =  0;

	private static final Logger _logger = LoggerFactory.getLogger(QpidSend.class);
	Reporter report;
	TestConfiguration config;

	public QpidSend(Reporter report, TestConfiguration config, Connection con, Destination dest)
	{
		this(report,config, con, dest, UUID.randomUUID().toString());
	}

	public QpidSend(Reporter report, TestConfiguration config, Connection con, Destination dest, String prefix)
	{
		//System.out.println("Producer ID : " + id);
		this.report = report;
		this.config = config;
		this.con = con;
		this.dest = dest;
	}

	public void setUp() throws Exception
	{
		con.start();
		if (config.isTransacted())
		{
			session = con.createSession(true, Session.SESSION_TRANSACTED);
		}
		else
		{
			session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
		}

		durable = config.isDurable();
		rateLimitProducer = config.getSendRate() > 0 ? true : false;
		if (_logger.isDebugEnabled() && rateLimitProducer)
		{
			_logger.debug("The test will attempt to limit the producer to " + config.getSendRate() + " msg/sec");
		}

		transacted = config.isTransacted();
		txSize =  config.getTransactionSize();

		msgType = MessageType.getType(config.getMessageType());
		// if message caching is enabled we pre create the message
		// else we pre create the payload
		if (config.isCacheMessage())
		{
			cacheMsg = true;
			msg = createMessage(createPayload(config.getMsgSize()));
			msg.setJMSDeliveryMode(durable?
					DeliveryMode.PERSISTENT :
						DeliveryMode.NON_PERSISTENT
					);
		}
		else if (config.isRandomMsgSize())
		{
			random = new Random(20080921);
			randomMsgSize = true;
			msgSizeRange = config.getMsgSize();
			payloads = new ArrayList<Object>(msgSizeRange);

			for (int i=0; i < msgSizeRange; i++)
			{
				payloads.add(createPayload(i));
			}
		}
		else
		{
			payload = createPayload(config.getMsgSize());
		}

		producer = session.createProducer(dest);
		if (_logger.isDebugEnabled())
		{
			_logger.debug("Producer: " + /*id +*/ " Sending messages to: " + ((AMQDestination)dest).getAddressName());
		}
		producer.setDisableMessageID(config.isDisableMessageID());
		//we add a separate timestamp to allow interoperability with other clients.
		producer.setDisableMessageTimestamp(true);
		if (config.getTTL() > 0)
		{
			producer.setTimeToLive(config.getTTL());
		}
		if (config.getPriority() > 0)
		{
			producer.setPriority(config.getPriority());
		}
	}

	Object createPayload(int size)
	{
		if (msgType == MessageType.TEXT)
		{
			return MessageFactory.createMessagePayload(size);
		}
		else
		{
			return MessageFactory.createMessagePayload(size).getBytes();
		}
	}

	Message createMessage(Object payload) throws Exception
	{
		if (msgType == MessageType.TEXT)
		{
			return session.createTextMessage((String)payload);
		}
		else
		{
			BytesMessage m = session.createBytesMessage();
			m.writeBytes((byte[])payload);
			return m;
		}
	}

	protected Message getNextMessage() throws Exception
	{
		if (cacheMsg)
		{
			return msg;
		}
		else
		{
			Message m;

			if (!randomMsgSize)
			{
				m = createMessage(payload);
			}
			else
			{
				m = createMessage(payloads.get(random.nextInt(msgSizeRange)));
			}
			m.setJMSDeliveryMode(durable?
					DeliveryMode.PERSISTENT :
						DeliveryMode.NON_PERSISTENT
					);
			return m;
		}
	}

	public void commit() throws Exception
	{
		session.commit();
	}

	public void send() throws Exception
	{
		send(config.getMsgCount());
	}

	public void send(int count) throws Exception
	{
		int sendRate = config.getSendRate();
		if (rateLimitProducer)
		{
			int iterations = count/sendRate;
			int remainder = count%sendRate;
			for (int i=0; i < iterations; i++)
			{
				long iterationStart  = System.currentTimeMillis();
				sendMessages(sendRate);
				long elapsed = System.currentTimeMillis() - iterationStart;
				long diff  = Clock.SEC - elapsed;
				if (diff > 0)
				{
					// We have sent more messages in a sec than specified by the rate.
					Thread.sleep(diff);
				}
			}
			sendMessages(remainder);
		}
		else
		{
			sendMessages(count);
		}
	}

	private void sendMessages(int count) throws Exception
	{
		boolean isTimestamp = !config.isDisableTimestamp();
		long s = System.currentTimeMillis();
		for(int i=0; i < count; i++ )
		{
			Message msg = getNextMessage();
			if (isTimestamp)
			{
				msg.setLongProperty(TestConfiguration.TIMESTAMP, System.currentTimeMillis());
			}
			producer.send(msg);
			//report.message(msg);
			totalMsgCount++;

			if ( transacted && ((totalMsgCount) % txSize == 0))
			{
				session.commit();
			}
		}
		long e = System.currentTimeMillis() - s;
		//System.out.println("Rate : " + totalMsgCount/e);
	}

	public void resetCounters()
	{
		totalMsgCount = 0;
		report.clear();
	}

	public void sendEndMessage() throws Exception
	{
		Message msg = session.createTextMessage(TestConfiguration.EOS);
		producer.send(msg);
	}

	public void tearDown() throws Exception
	{
		session.close();
	}

	public static void main(String[] args) throws Exception
	{
		TestConfiguration config = new JVMArgConfiguration();
		Reporter reporter = new BasicReporter(Throughput.class,
				System.out,
				config.reportEvery(),
				config.isReportHeader()
				);
		Destination dest = AMQDestination.createDestination(config.getAddress(), false);
		QpidSend sender = new QpidSend(reporter,config, config.createConnection(),dest);
		sender.setUp();
		sender.send();
		if (config.getSendEOS() > 0)
		{
			sender.sendEndMessage();
		}
		if (config.isReportTotal())
		{
			reporter.report();
		}
		sender.tearDown();
	}
}
