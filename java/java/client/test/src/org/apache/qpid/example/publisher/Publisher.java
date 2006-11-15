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

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;

import org.apache.qpid.jms.Session;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.DeliveryMode;
import javax.jms.Queue;
import javax.jms.MessageProducer;
import javax.jms.Connection;


import org.apache.qpid.example.shared.ConnectionException;
import org.apache.qpid.example.shared.Statics;

public class Publisher
{
    private static final Logger _log = Logger.getLogger(Publisher.class);

    protected Connection _connection;

    protected Session _session;

    private MessageProducer _producer;

    protected String _destinationDir;

    protected String _name = "Publisher";

    protected Queue _destination;

    //constructor for use with a single host
    public Publisher(String host, int port, String clientID, String queueName,
                     String user, String password, String virtualPath, String destinationDir)
    {
        try
        {
            createConnection(host, port, clientID, user, password, virtualPath);

            //create a transactional session
            _session = (Session) _connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

            //now using a queue rather than a topic
            //AMQTopic destination = new AMQTopic(topicName);
            //Queue is non-exclusive and not deleted when last consumer detaches
            _destination = _session.createQueue(queueName);

            //create a message producer
            _producer = _session.createProducer(_destination);

            //set destination dir for files that have been processed
            _destinationDir = destinationDir;

            _connection.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            _log.error(e);
        }
    }

    //constructor that allows for multiple host details to be provided for failover
    public Publisher(String hostdetails, String clientID, String queueName,
                     String user, String password, String virtualPath, String destinationDir)
    {
        try
        {
            if (queueName==null||queueName.length()==0)
            {
                queueName = Statics.QUEUE_NAME;
            }
            createConnectionWithFailover(hostdetails, clientID, user, password, virtualPath);

            //create a transactional session
            _session = (Session) _connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

            //now using a queue rather than a topic
            //AMQTopic destination = new AMQTopic(topicName);
            //Queue is non-exclusive and not deleted when last consumer detaches
            _destination = _session.createQueue(queueName);

            //create a message producer
            _producer = _session.createProducer(_destination);

            //set destination dir for files that have been processed
            _destinationDir = destinationDir;

            _connection.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            _log.error(e);
        }
    }

    /*
    * Publishes a non-persistent message using transacted session
    */
    public boolean sendMessage(Message message)
    {
        try
        {
            //Send message via our producer which is not persistent
            _producer.send(message, DeliveryMode.NON_PERSISTENT, _producer.getPriority(), _producer.getTimeToLive());

            //commit the message send and close the transaction
            _session.commit();

        }
        catch (JMSException e)
        {
            //Have to assume our commit failed and rollback here
            try
            {
                _session.rollback();
                _log.error(e);
                e.printStackTrace();
                return false;
            }
            catch (JMSException j)
            {
                _log.error("Unable to rollback publish transaction ",e);
                return false;
            }
        }

        _log.info(_name + " finished sending message: " + message);
        return true;
    }

    public void cleanup()
    {
        try
        {
            if (_connection != null)
            {
                _connection.stop();
                _connection.close();
            }
            _connection = null;
            _producer = null;
        }
        catch(Exception e)
        {
            System.err.println("Error trying to cleanup publisher " + e);
            System.exit(1);
        }
    }

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

    //ONly using one set of host details
    private void createConnection(String host, int port, String clientID, String user, String password, String virtualPath)
                                   throws ConnectionException
    {
        try
        {
            _connection = new AMQConnection(host, port, user, password, clientID, virtualPath);
        }
        catch (Exception e)
        {
            throw new ConnectionException(e.toString());
        }
    }

    //Create connection with more than one set of host details for failover
    private void createConnectionWithFailover(String hostdetails, String clientID, String user, String password, String virtualPath)
                                   throws ConnectionException
    {
        try
        {
            _connection = new AMQConnection(hostdetails, user, password, clientID, virtualPath);
        }
        catch (Exception e)
        {
            throw new ConnectionException(e.toString());
        }
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String _name) {
        this._name = _name;
    }
}

