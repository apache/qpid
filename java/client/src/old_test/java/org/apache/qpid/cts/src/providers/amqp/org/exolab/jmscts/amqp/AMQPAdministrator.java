/**
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright
 *    statements and notices.  Redistributions must also contain a
 *    copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the
 *    above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. The name "Exolab" must not be used to endorse or promote
 *    products derived from this Software without prior written
 *    permission of Exoffice Technologies.  For written permission,
 *    please contact jima@intalio.com.
 *
 * 4. Products derived from this Software may not be called "Exolab"
 *    nor may "Exolab" appear in their names without prior written
 *    permission of Exoffice Technologies. Exolab is a registered
 *    trademark of Exoffice Technologies.
 *
 * 5. Due credit should be given to the Exolab Project
 *    (http://www.exolab.org/).
 *
 * THIS SOFTWARE IS PROVIDED BY EXOFFICE TECHNOLOGIES AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * EXOFFICE TECHNOLOGIES OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2001, 2003 (C) Exoffice Technologies Inc. All Rights Reserved.
 *
 */
package org.apache.qpid.cts.src.providers.amqp.org.exolab.jmscts.amqp;

import org.apache.qpid.client.*;
import org.exolab.jmscts.provider.Administrator;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.net.InetAddress;
import java.util.HashMap;

/**
 * This class provides methods for obtaining and manipulating administered
 * objects managed by the Sonicmq implementation of JMS
 *
 */
class AMQPAdministrator implements Administrator {
    // AMQ Connection configuration
    private int port = 5672;
    private String host = "localhost";
    private String user = "guest";
    private String pass = "guest";
    private String vhost = "/test";

    // The cached broker connection & session
    private AMQConnection _connection = null;
    private Session _session = null;

    // Factory request names
    private static final String QUEUE_CONNECTION_FACTORY = "QueueConnectionFactory";
    private static final String TOPIC_CONNECTION_FACTORY = "TopicConnectionFactory";

    /**
     * The cache of known administered objects
     */
    private HashMap<String, Object> _directory = new HashMap<String, Object>();

    /**
     * Returns the name of the QueueConnectionFactory bound in JNDI
     *
     * @return the default QueueConnectionFactory name
     */
    public String getQueueConnectionFactory() {
        return QUEUE_CONNECTION_FACTORY;
    }

    /**
     * Returns the name of the TopicConnectionFactory bound in JNDI
     *
     * @return the default TopicConnectionFactory name
     */
    public String getTopicConnectionFactory() {
        return TOPIC_CONNECTION_FACTORY;
    }

    /**
     * Returns the name of the XAQueueConnectionFactory bound in JNDI
     *
     * @return the default XAQueueConnectionFactory name
     */
    public String getXAQueueConnectionFactory() {
        return null;
    }

    /**
     * Returns the name of the XATopicConnectionFactory bound in JNDI
     *
     * @return the default XATopicConnectionFactory name
     */
    public String getXATopicConnectionFactory() {
        return null;
    }

    /**
     * Look up the named administered object
     *
     * @param name the name that the administered object is bound to
     * @return the administered object bound to name
     * @throws NamingException if the object is not bound, or the lookup fails
     */
    public Object lookup(String name) throws NamingException {
        Object result = _directory.get(name);
        if (result == null) {
            if (name.equals(QUEUE_CONNECTION_FACTORY)) {
                _directory.put(QUEUE_CONNECTION_FACTORY, new AMQConnectionFactory(host, port, user, pass, vhost));
            } else if (name.equals(TOPIC_CONNECTION_FACTORY)) {
                _directory.put(TOPIC_CONNECTION_FACTORY, new AMQConnectionFactory(host, port, user, pass, vhost));
            } else {
                throw new NameNotFoundException("Name not found: " + name);
            }
        }
        return result;
    }

    /**
     * Create an administered destination
     *
     * @param name the destination name
     * @param queue if true, create a queue, else create a topic
     * @throws JMSException if the destination cannot be created
     */
    public void createDestination(String name, boolean queue)
        throws JMSException {
        AMQDestination destination = null;

        try {
            if (queue) {
                destination = new AMQQueue(name);
                createConsumer(destination);
            } else {
                destination = new AMQTopic(name);
                createConsumer(destination);
            }

            _directory.put(name, destination);
        } catch (Exception exception) {
            JMSException error = new JMSException(exception.getMessage());
            error.setLinkedException(exception);
            throw error;
        }
    }

    /**
     * Destroy an administered destination
     *
     * @param name the destination name
     * @throws JMSException if the destination cannot be destroyed
     */
    public void destroyDestination(String name)
        throws JMSException {

        try {
            Destination destination = (Destination) lookup(name);
            _directory.remove(name);
        } catch (NamingException exception) {
            JMSException error = new JMSException(exception.getMessage());
            error.setLinkedException(exception);
            throw error;
        } catch (Exception exception) {
            JMSException error = new JMSException(exception.getMessage());
            error.setLinkedException(exception);
            throw error;
        }
    }

    /**
     * Returns true if an administered destination exists
     *
     * @param name the destination name
     * @throws JMSException for any internal JMS provider error
     */
    public boolean destinationExists(String name)
        throws JMSException {

        boolean exists = false;
        try {
            lookup(name);
            exists = true;
        } catch (NameNotFoundException ignore) {
        } catch (Exception exception) {
            JMSException error = new JMSException(exception.getMessage());
            error.setLinkedException(exception);
            throw error;
        }
        return exists;
    }

    public void initialise() throws JMSException {
        try {
            InetAddress address = InetAddress.getLocalHost();
            _connection = new AMQConnection(host, port, user, pass,
                                                  address.getHostName(), vhost);
            _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (Exception exception) {
            JMSException error = new JMSException(exception.getMessage());
            error.setLinkedException(exception);
            throw error;
        }
    }

    public synchronized void cleanup() {
        try {
            _connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
        _connection = null;
        _session = null;
        _directory.clear();
    }

    MessageConsumer createConsumer(AMQDestination destination) throws JMSException
    {
        return ((AMQSession)_session).createConsumer(destination, /*pre-fetch*/0, false, /*exclusive*/false, null);
    }
} //-- AMQPAdministrator
