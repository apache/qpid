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
package org.apache.qpid.client;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnection;
import javax.jms.XATopicConnectionFactory;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.UUID;


public class AMQConnectionFactory implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory,
                                             ObjectFactory, Referenceable, XATopicConnectionFactory,
                                             XAQueueConnectionFactory, XAConnectionFactory, Serializable
{
    protected static final String NO_URL_CONFIGURED = "The connection factory wasn't created with a proper URL, the connection details are empty";


    private ConnectionURL _connectionDetails;

    // The default constructor is necessary to allow AMQConnectionFactory to be deserialised from JNDI
    public AMQConnectionFactory()
    {
    }

    public AMQConnectionFactory(final String url) throws URLSyntaxException
    {
        if (url == null)
        {
            throw new IllegalArgumentException("url cannot be null");
        }

        _connectionDetails = new AMQConnectionURL(url);
    }

    public AMQConnectionFactory(ConnectionURL url)
    {
        if (url == null)
        {
            throw new IllegalArgumentException("url cannot be null");
        }

        _connectionDetails = url;
    }

    /**
     * @return the virtualPath of the connection details.
     */
    public final String getVirtualPath()
    {
        return _connectionDetails.getVirtualHost();
    }

    public static String getUniqueClientID()
    {
        try
        {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName() + System.currentTimeMillis();
        }
        catch (UnknownHostException e)
        {
            return "UnknownHost" + UUID.randomUUID();
        }
    }

    public Connection createConnection() throws JMSException
    {
        if(_connectionDetails == null)
        {
            throw new JMSException(NO_URL_CONFIGURED);
        }

        try
        {
            if (_connectionDetails.getClientName() == null || _connectionDetails.getClientName().equals(""))
            {
                _connectionDetails.setClientName(getUniqueClientID());
            }
            return new AMQConnection(_connectionDetails);
        }
        catch (Exception e)
        {
            JMSException jmse = new JMSException("Error creating connection: " + e.getMessage());
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }
    }

    public Connection createConnection(String userName, String password) throws JMSException
    {
        return createConnection(userName, password, null);
    }
    
    public Connection createConnection(String userName, String password, String id) throws JMSException
    {
        if (_connectionDetails != null)
        {
            try
            {
                ConnectionURL connectionDetails = new AMQConnectionURL(_connectionDetails.getURL());
                connectionDetails.setUsername(userName);
                connectionDetails.setPassword(password);
                
                if (id != null && !id.equals(""))
                {
                    connectionDetails.setClientName(id);
                } 
                else if (connectionDetails.getClientName() == null || connectionDetails.getClientName().equals(""))
                {
                    connectionDetails.setClientName(getUniqueClientID());
                }
                return new AMQConnection(connectionDetails);
            }
            catch (Exception e)
            {
                JMSException jmse = new JMSException("Error creating connection: " + e.getMessage());
                jmse.setLinkedException(e);
                jmse.initCause(e);
                throw jmse;
            }
        }
        else
        {
            throw new JMSException(NO_URL_CONFIGURED);
        }
    }

    public QueueConnection createQueueConnection() throws JMSException
    {
        return (QueueConnection) createConnection();
    }

    public QueueConnection createQueueConnection(String username, String password) throws JMSException
    {
        return (QueueConnection) createConnection(username, password);
    }

    public TopicConnection createTopicConnection() throws JMSException
    {
        return (TopicConnection) createConnection();
    }

    public TopicConnection createTopicConnection(String username, String password) throws JMSException
    {
        return (TopicConnection) createConnection(username, password);
    }


    public ConnectionURL getConnectionURL()
    {
        return _connectionDetails;
    }

    public String getConnectionURLString()
    {
        return _connectionDetails.toString();
    }

    //setter necessary to use instances created with the default constructor (which we can't remove)
    public final void setConnectionURLString(String url) throws URLSyntaxException
    {
        _connectionDetails = new AMQConnectionURL(url);
    }

    /**
     * JNDI interface to create objects from References.
     *
     * @param obj  The Reference from JNDI
     * @param name
     * @param ctx
     * @param env
     *
     * @return AMQConnection,AMQTopic,AMQQueue, or AMQConnectionFactory.
     *
     * @throws Exception
     */
    public Object getObjectInstance(Object obj, Name name, Context ctx, Hashtable env) throws Exception
    {
        if (obj instanceof Reference)
        {
            Reference ref = (Reference) obj;

            if (ref.getClassName().equals(AMQConnection.class.getName()))
            {
                RefAddr addr = ref.get(AMQConnection.class.getName());

                if (addr != null)
                {
                    return new AMQConnection((String) addr.getContent());
                }
            }

            if (ref.getClassName().equals(AMQQueue.class.getName()))
            {
                RefAddr addr = ref.get(AMQQueue.class.getName());

                if (addr != null)
                {
                    return new AMQQueue(new AMQBindingURL((String) addr.getContent()));
                }
            }

            if (ref.getClassName().equals(AMQTopic.class.getName()))
            {
                RefAddr addr = ref.get(AMQTopic.class.getName());

                if (addr != null)
                {
                    return new AMQTopic(new AMQBindingURL((String) addr.getContent()));
                }
            }

            if (ref.getClassName().equals(AMQConnectionFactory.class.getName()))
            {
                RefAddr addr = ref.get(AMQConnectionFactory.class.getName());

                if (addr != null)
                {
                    return new AMQConnectionFactory((String) addr.getContent());
                }
            }

        }
        return null;
    }


    public Reference getReference() throws NamingException
    {
        return new Reference(
                AMQConnectionFactory.class.getName(),
                new StringRefAddr(AMQConnectionFactory.class.getName(), _connectionDetails.getURL()),
                             AMQConnectionFactory.class.getName(), null);          // factory location
    }

    // ---------------------------------------------------------------------------------------------------
    // the following methods are provided for XA compatibility
    // Those methods are only supported by 0_10 and above 
    // ---------------------------------------------------------------------------------------------------

    /**
     * Creates a XAConnection with the default user identity.
     * <p> The XAConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created XAConnection
     * @throws JMSException         If creating the XAConnection fails due to some internal error.
     * @throws javax.jms.JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAConnection createXAConnection() throws JMSException
    {
        try
        {
            return new XAConnectionImpl(_connectionDetails);
        }
        catch (Exception e)
        {
            JMSException jmse = new JMSException("Error creating connection: " + e.getMessage());
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }
    }

    /**
     * Creates a XAConnection with the specified user identity.
     * <p> The XAConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @param username the caller's user name
     * @param password the caller's password
     * @return A newly created XAConnection.
     * @throws JMSException         If creating the XAConnection fails due to some internal error.
     * @throws javax.jms.JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAConnection createXAConnection(String username, String password) throws JMSException
    {
        if (_connectionDetails != null)
        {
            try
            {
                ConnectionURL connectionDetails = new AMQConnectionURL(_connectionDetails.toString());
                connectionDetails.setUsername(username);
                connectionDetails.setPassword(password);
    
                if (connectionDetails.getClientName() == null || connectionDetails.getClientName().equals(""))
                {
                    connectionDetails.setClientName(getUniqueClientID());
                }
                return new XAConnectionImpl(connectionDetails);
            }
            catch (Exception e)
            {
                JMSException jmse = new JMSException("Error creating XA Connection: " + e.getMessage());
                jmse.setLinkedException(e);
                jmse.initCause(e);
                throw jmse;
            }
        }
        else
        {
            throw new JMSException(NO_URL_CONFIGURED);
        }        
    }


    /**
     * Creates a XATopicConnection with the default user identity.
     * <p> The XATopicConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created XATopicConnection
     * @throws JMSException         If creating the XATopicConnection fails due to some internal error.
     * @throws javax.jms.JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XATopicConnection createXATopicConnection() throws JMSException
    {
        return (XATopicConnection) createXAConnection();
    }

    /**
     * Creates a XATopicConnection with the specified user identity.
     * <p> The XATopicConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @param username the caller's user name
     * @param password the caller's password
     * @return A newly created XATopicConnection.
     * @throws JMSException         If creating the XATopicConnection fails due to some internal error.
     * @throws javax.jms.JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XATopicConnection createXATopicConnection(String username, String password) throws JMSException
    {
         return (XATopicConnection) createXAConnection(username, password);
    }

    /**
     * Creates a XAQueueConnection with the default user identity.
     * <p> The XAQueueConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created XAQueueConnection
     * @throws JMSException         If creating the XAQueueConnection fails due to some internal error.
     * @throws javax.jms.JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAQueueConnection createXAQueueConnection() throws JMSException
    {
       return (XAQueueConnection) createXAConnection();
    }

    /**
     * Creates a XAQueueConnection with the specified user identity.
     * <p> The XAQueueConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @param username the caller's user name
     * @param password the caller's password
     * @return A newly created XAQueueConnection.
     * @throws JMSException         If creating the XAQueueConnection fails due to some internal error.
     * @throws javax.jms.JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAQueueConnection createXAQueueConnection(String username, String password) throws JMSException
    {
        return (XAQueueConnection) createXAConnection(username, password);
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AMQConnectionFactory that = (AMQConnectionFactory) o;

        if (_connectionDetails != null
                ? !_connectionDetails.equals(that._connectionDetails)
                : that._connectionDetails != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return _connectionDetails != null ? _connectionDetails.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return "AMQConnectionFactory{" +
               "_connectionDetails=" + _connectionDetails +
               '}';
    }
}
