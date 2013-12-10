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
package org.apache.qpid.jca.example.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.apache.qpid.jca.example.ejb.QpidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidRequestResponseClient implements MessageListener, Runnable
{
    private static final Logger _log = LoggerFactory.getLogger(QpidRequestResponseClient.class);

    private static final String DEFAULT_CF_JNDI = "QpidConnectionFactory";
    private static final String DEFAULT_DESTINATION_JNDI = "QpidRequestQueue";
    private static final String DEFAULT_MESSAGE = "Hello, World!";
    private static final int DEFAULT_MESSAGE_COUNT = 1;
    private static final int DEFAULT_THREAD_COUNT = 1;
    private static CountDownLatch THREAD_LATCH;
    private static InitialContext CONTEXT;

    private ConnectionFactory _connectionFactory;
    private Connection _connection;
    private Session _session;
    private CountDownLatch _latch = null;
    private int _count = DEFAULT_MESSAGE_COUNT;

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        int threadCount =  (System.getProperty("thread.count") == null)
            ? DEFAULT_THREAD_COUNT : Integer.valueOf(System.getProperty("thread.count"));

        _log.debug("Creating " + threadCount + " threads for execution.");

        THREAD_LATCH = new CountDownLatch(threadCount);

        CONTEXT = new InitialContext();

        for(int i = 0; i < threadCount; i++)
        {
            new Thread(new QpidRequestResponseClient()).start();
        }

        _log.debug("Waiting for " + threadCount + " to finish.");
        THREAD_LATCH.await(10, TimeUnit.SECONDS);

        QpidUtil.closeResources(CONTEXT);
    }

    @Override
        public void onMessage(Message message)
        {
            _latch.countDown();

            if(message instanceof TextMessage)
            {
                try
                {
                    _log.debug("Thread " + Thread.currentThread().getId() + " received response message with content " + ((TextMessage)message).getText());
                }
                catch(Exception e)
                {
                    _log.error(e.getMessage(), e);
                }
            }

            if(_latch.getCount() == _count)
            {
                QpidUtil.closeResources(_session, _connection);
            }

            THREAD_LATCH.countDown();

        }

    public void run()
    {
        MessageProducer producer = null;
        Destination requestQueue = null;
        Destination responseQueue = null;

        String cfName = (System.getProperty("qpid.cf.name") == null) ? DEFAULT_CF_JNDI : System.getProperty("qpid.cf.name");
        String destName = (System.getProperty("qpid.dest.name") == null) ? DEFAULT_DESTINATION_JNDI : System.getProperty("qpid.dest.name");

        try
        {
            _count =  (System.getProperty("message.count") == null) ? DEFAULT_MESSAGE_COUNT : Integer.valueOf(System.getProperty("message.count"));
            _latch = new CountDownLatch(_count);

            _connectionFactory = (ConnectionFactory)QpidTestUtil.getFromJNDI(CONTEXT, cfName);
            requestQueue = (Destination)QpidTestUtil.getFromJNDI(CONTEXT, destName);
            _connection = _connectionFactory.createConnection();
            _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            producer = _session.createProducer(requestQueue);
            responseQueue = _session.createTemporaryQueue();
            _session.createConsumer(responseQueue).setMessageListener(this);


            _connection.start();

            String content = (System.getProperty("qpid.message") == null) ? DEFAULT_MESSAGE : System.getProperty("qpid.message");

            for(int i = 0; i < _count; i++)
            {
                TextMessage message = _session.createTextMessage();
                message.setText(content);
                message.setJMSReplyTo(responseQueue);
                producer.send(message);

            }

            _latch.await();

        }
        catch(Exception e)
        {
            _log.error(e.getMessage(), e);
        }
        finally
        {
            QpidUtil.closeResources(producer);
        }

    }

}
