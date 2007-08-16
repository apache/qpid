package org.apache.qpidity.jms;

import javax.jms.*;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.apache.qpidity.QpidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionFactoryImpl implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory,
                                              XATopicConnectionFactory, XAQueueConnectionFactory, XAConnectionFactory,
                                              Referenceable
{
    /**
     * this ConnectionFactoryImpl's logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(ConnectionFactoryImpl.class);

    /**
     * The virtual host on which the broker is deployed.
     */
    private String _host;
    /**
     * The port on which the broker is listening for connection.
     */
    private int _port;
    /**
     * The default user name used of user identification.
     */
    private String _defaultUsername;
    /**
     * The default password used of user identification.
     */
    private String _defaultPassword;
    /**
     * The virtual host on which the broker is deployed.
     */
    private String _virtualHost;
    /**
     * The URL used to build this factory, (not yet supported)
     */
    private String _url;

    // Undefined at the moment
    public ConnectionFactoryImpl(String url)
    {
        _url = url;
    }

    /**
     * Create a connection.
     *
     * @param host            The broker host name.
     * @param port            The port on which the broker is listening for connection.
     * @param virtualHost     The virtual host on which the broker is deployed.
     * @param defaultUsername The user name used of user identification.
     * @param defaultPassword The password used of user identification.
     */
    public ConnectionFactoryImpl(String host, int port, String virtualHost, String defaultUsername,
                                 String defaultPassword)
    {
        _host = host;
        _port = port;
        _defaultUsername = defaultUsername;
        _defaultPassword = defaultPassword;
        _virtualHost = virtualHost;
    }

    //-- Interface ConnectionFactory

    /**
     * Creates a connection with the default user identity.
     * <p> The connection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created connection.
     * @throws JMSException         If creating the connection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public Connection createConnection() throws JMSException
    {
        try
        {
            return new ConnectionImpl(_host, _port, _virtualHost, _defaultUsername, _defaultPassword);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Creates a connection with the specified user identity.
     * <p> The connection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @param username the caller's user name
     * @param password the caller's password
     * @return A newly created connection.
     * @throws JMSException         If creating the connection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public Connection createConnection(String username, String password) throws JMSException
    {
        try
        {
            return new ConnectionImpl(_host, _port, _virtualHost, username, password);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    // ----------------------------------------
    // Support for JMS 1.0 classes
    // ----------------------------------------
    //--- Interface QueueConnection
    /**
     * Creates a queueConnection with the default user identity.
     * <p> The queueConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created queueConnection
     * @throws JMSException         If creating the queueConnection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public QueueConnection createQueueConnection() throws JMSException
    {
        try
        {
            return new QueueConnectionImpl(_host, _port, _virtualHost, _defaultUsername, _defaultPassword);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Creates a queueConnection with the specified user identity.
     * <p> The queueConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @param username the caller's user name
     * @param password the caller's password
     * @return A newly created queueConnection.
     * @throws JMSException         If creating the queueConnection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public QueueConnection createQueueConnection(String username, String password) throws JMSException
    {
        try
        {
            return new QueueConnectionImpl(_host, _port, _virtualHost, username, password);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    //--- Interface TopicConnection
    /**
     * Creates a topicConnection with the default user identity.
     * <p> The topicConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created topicConnection
     * @throws JMSException         If creating the topicConnection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public TopicConnection createTopicConnection() throws JMSException
    {
        try
        {
            return new TopicConnectionImpl(_host, _port, _virtualHost, _defaultUsername, _defaultPassword);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Creates a topicConnection with the specified user identity.
     * <p> The topicConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @param username the caller's user name
     * @param password the caller's password
     * @return A newly created topicConnection.
     * @throws JMSException         If creating the topicConnection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public TopicConnection createTopicConnection(String username, String password) throws JMSException
    {
        try
        {
            return new TopicConnectionImpl(_host, _port, _virtualHost, username, password);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // the following methods are provided for XA compatibility
    // ---------------------------------------------------------------------------------------------------

    /**
     * Creates a XAConnection with the default user identity.
     * <p> The XAConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created XAConnection
     * @throws JMSException         If creating the XAConnection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAConnection createXAConnection() throws JMSException
    {
        try
        {
            return new XAConnectionImpl(_host, _port, _virtualHost, _defaultUsername, _defaultPassword);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
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
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAConnection createXAConnection(String username, String password) throws JMSException
    {
        try
        {
            return new XAConnectionImpl(_host, _port, _virtualHost, username, password);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
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
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XATopicConnection createXATopicConnection() throws JMSException
    {
        try
        {
            return new XATopicConnectionImpl(_host, _port, _virtualHost, _defaultUsername, _defaultPassword);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
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
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XATopicConnection createXATopicConnection(String username, String password) throws JMSException
    {
        try
        {
            return new XATopicConnectionImpl(_host, _port, _virtualHost, username, password);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Creates a XAQueueConnection with the default user identity.
     * <p> The XAQueueConnection is created in stopped mode. No messages
     * will be delivered until the <code>Connection.start</code> method
     * is explicitly called.
     *
     * @return A newly created XAQueueConnection
     * @throws JMSException         If creating the XAQueueConnection fails due to some internal error.
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAQueueConnection createXAQueueConnection() throws JMSException
    {
        try
        {
            return new XAQueueConnectionImpl(_host, _port, _virtualHost, _defaultUsername, _defaultPassword);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
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
     * @throws JMSSecurityException If client authentication fails due to an invalid user name or password.
     */
    public XAQueueConnection createXAQueueConnection(String username, String password) throws JMSException
    {
        try
        {
            return new XAQueueConnectionImpl(_host, _port, _virtualHost, username, password);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("PRoblem when creating connection", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    // ----------------------------------------
    // Support for JNDI
    // ----------------------------------------

    public Reference getReference() throws NamingException
    {
        return new Reference(ConnectionFactoryImpl.class.getName(),
                             new StringRefAddr(ConnectionFactoryImpl.class.getName(), _url));
    }

}
