/*
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
 */
package org.apache.qpid.example.publisher;

import org.apache.qpid.client.AMQConnectionFactory;

import javax.jms.*;

import javax.naming.InitialContext;

import org.apache.qpid.example.shared.InitialContextHelper;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class Publisher
{
    private static final Logger _log = LoggerFactory.getLogger(Publisher.class);

    protected InitialContextHelper _contextHelper;

    private Connection _connection;

    private Session _session;

    private MessageProducer _producer;

    private String _destinationDir;

    private String _name = "Publisher";

    private Destination _destination;

    private static final String _defaultDestinationDir = "/tmp";

    /**
     * Creates a Publisher instance using properties from example.properties
     * See InitialContextHelper for details of how context etc created
     */
    public Publisher()
    {
        try
        {
            //get an initial context from default properties
            _contextHelper = new InitialContextHelper(null);
            InitialContext ctx = _contextHelper.getInitialContext();

            //then create a connection using the AMQConnectionFactory
            AMQConnectionFactory cf = (AMQConnectionFactory) ctx.lookup("local");
            setConnection(cf.createConnection());

            getConnection().setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    // The connection may have broken invoke reconnect code if available.
                    System.err.println("ExceptionListener caught: " + jmse);
                    //System.exit(0);
                }
            });

            //create a transactional session
            setSession(getConnection().createSession(true, Session.AUTO_ACKNOWLEDGE));

            //lookup the example queue and use it
            //Queue is non-exclusive and not deleted when last consumer detaches
            setDestination((Queue) ctx.lookup("MyQueue"));

            //create a message producer
            setProducer(getSession().createProducer(getDestination()));

            //set destination dir for files that have been processed
            setDestinationDir(get_defaultDestinationDir());

            getConnection().start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            _log.error("Exception", e);
        }
    }

    public static String get_defaultDestinationDir()
    {
        return _defaultDestinationDir;
    }

    /**
     * Creates and sends the number of messages specified in the param
     */
    public void sendMessage(int numMessages)
    {
        try
        {
            TextMessage txtMessage = getSession().createTextMessage("msg");
            for (int i=0;i<numMessages;i++)
            {
                sendMessage(txtMessage);
                _log.info("Sent: " + i);
            }
        }
        catch (JMSException j)
        {
            _log.error("Exception in sendMessage" + j);
        }


    }

    /**
     * Publishes a non-persistent message using transacted session
     * Note that persistent is the default mode for send - so need to specify for transient
     */
    public boolean sendMessage(Message message)
    {
        try
        {
            //Send message via our producer which is not persistent
            getProducer().send(message, DeliveryMode.PERSISTENT, getProducer().getPriority(), getProducer().getTimeToLive());

            //commit the message send and close the transaction
            getSession().commit();

        }
        catch (JMSException e)
        {
            //Have to assume our commit failed and rollback here
            try
            {
                getSession().rollback();
                _log.error("JMSException", e);
                e.printStackTrace();
                return false;
            }
            catch (JMSException j)
            {
                _log.error("Unable to rollback publish transaction ",e);
                return false;
            }
        }

        //_log.info(_name + " finished sending message: " + message);
        return true;
    }

    /**
     * Cleanup resources before exit
     */
    public void cleanup()
    {
        try
        {
            if (getConnection() != null)
            {
                getConnection().stop();
                getConnection().close();
            }
            setConnection(null);
            setProducer(null);
        }
        catch(Exception e)
        {
            _log.error("Error trying to cleanup publisher " + e);
            System.exit(1);
        }
    }

    /**
     * Exposes session
     * @return  Session
     */
    public Session getSession()
    {
        return _session;
    }

    public String getDestinationDir()
    {
        return _destinationDir;
    }

    public void setDestinationDir(String destinationDir)
    {
        _destinationDir = destinationDir;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String _name) {
        this._name = _name;
    }


    public Connection getConnection()
    {
        return _connection;
    }

    public void setConnection(Connection connection)
    {
        _connection = connection;
    }

    public void setSession(Session session)
    {
        _session = session;
    }

    public MessageProducer getProducer()
    {
        return _producer;
    }

    public void setProducer(MessageProducer producer)
    {
        _producer = producer;
    }

    public Destination getDestination()
    {
        return _destination;
    }

    public void setDestination(Destination destination)
    {
        _destination = destination;
    }
}

