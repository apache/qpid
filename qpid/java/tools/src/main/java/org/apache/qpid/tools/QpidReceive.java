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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.tools.report.BasicReporter;
import org.apache.qpid.tools.report.Reporter;
import org.apache.qpid.tools.report.Statistics.ThroughputAndLatency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidReceive implements MessageListener
{
	private static final Logger _logger = LoggerFactory.getLogger(QpidReceive.class);
	private final CountDownLatch testCompleted = new CountDownLatch(1);

	private Connection con;
	private Session session;
	private Destination dest;
	private MessageConsumer consumer;
	private boolean transacted = false;
	private boolean isRollback = false;
	private int txSize = 0;
	private int rollbackFrequency = 0;
	private int ackFrequency = 0;
	private int expected = 0;
	private int received = 0;
	private Reporter report;
	private TestConfiguration config;

	public QpidReceive(Reporter report, TestConfiguration config, Connection con, Destination dest)
	{
		this(report,config, con, dest, UUID.randomUUID().toString());
	}

	public QpidReceive(Reporter report, TestConfiguration config, Connection con, Destination dest, String prefix)
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
		else if (config.getAckFrequency() > 0)
		{
			session = con.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
		}
		else
		{
			session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
		}
		consumer = session.createConsumer(dest);
		consumer.setMessageListener(this);
		if (_logger.isDebugEnabled())
		{
			System.out.println("Consumer: " + /*id +*/ " Receiving messages from : " + ((AMQDestination)dest).getAddressName() + "\n");
		}

		transacted = config.isTransacted();
		txSize = config.getTransactionSize();
		isRollback = config.getRollbackFrequency() > 0;
		rollbackFrequency = config.getRollbackFrequency();
		ackFrequency = config.getAckFrequency();

		_logger.debug("Ready address : " + config.getReadyAddress());
		if (config.getReadyAddress() != null)
		{
			MessageProducer prod = session.createProducer(AMQDestination
					.createDestination(config.getReadyAddress(), false));
			prod.send(session.createMessage());
			if (_logger.isDebugEnabled())
			{
				_logger.debug("Sending message to ready address " + prod.getDestination());
			}
		}
	}

	public void resetCounters()
	{
		received = 0;
		expected = 0;
		report.clear();
	}

	public void onMessage(Message msg)
	{
		try
		{
			if (msg instanceof TextMessage &&
					TestConfiguration.EOS.equals(((TextMessage)msg).getText()))
			{
				testCompleted.countDown();
				return;
			}

			received++;
			report.message(msg);

			if (config.isPrintHeaders())
			{
				System.out.println(((AbstractJMSMessage)msg).toHeaderString());
			}

			if (config.isPrintContent())
			{
				System.out.println(((AbstractJMSMessage)msg).toBodyString());
			}

			if (transacted && (received % txSize == 0))
			{
				if (isRollback && (received % rollbackFrequency == 0))
				{
					session.rollback();
				}
				else
				{
					session.commit();
				}
			}  
			else if (ackFrequency > 0)
			{
				msg.acknowledge();
			}

			if (received >= expected)
			{
				testCompleted.countDown();
			}

		}
		catch(Exception e)
		{
			_logger.error("Error when receiving messages",e);
		}
	}

	public void waitforCompletion(int expected) throws Exception
	{
		this.expected = expected;
		testCompleted.await();
	}

	public void tearDown() throws Exception
	{
		session.close();
	}

	public static void main(String[] args) throws Exception
	{
		TestConfiguration config = new JVMArgConfiguration();
		Reporter reporter = new BasicReporter(ThroughputAndLatency.class,
				System.out,
				config.reportEvery(),
				config.isReportHeader());
		Destination dest = AMQDestination.createDestination(config.getAddress(), false);
		QpidReceive receiver = new QpidReceive(reporter,config, config.createConnection(),dest);
		receiver.setUp();
		receiver.waitforCompletion(config.getMsgCount() + config.getSendEOS());
		if (config.isReportTotal())
		{
			reporter.report();
		}
		receiver.tearDown();
	}

}
