package org.apache.qpidity.njms;

import javax.jms.*;
import javax.naming.*;
import javax.naming.spi.ObjectFactory;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.BrokerDetails;
import org.apache.qpidity.url.QpidURLImpl;
import org.apache.qpidity.url.QpidURL;
import org.apache.qpidity.url.BindingURLImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.net.MalformedURLException;

/**
 * Implements all the JMS connection factories.
 * <p> In all the implementations in our code base
 * when we create a Reference we pass in <code>ConnectionFactoryImpl</code> as the
 * factory for creating the objects. This is the factory (or
 * {@link ObjectFactory}) that is used to turn the description in to a real object.
 * <p>In our construction of the Reference the last param. is null,
 * we could put a url to a jar that contains our {@link ObjectFactory} so that
 * any of our objects stored in JNDI can be recreated without even having
 * the classes locally. As it is the <code>ConnectionFactoryImpl</code> must be on the
 * classpath when you do a lookup in a JNDI context.. else you'll get a
 * ClassNotFoundEx.
 */
public class ConnectionFactoryImpl implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory,
                                              XATopicConnectionFactory, XAQueueConnectionFactory, XAConnectionFactory,
                                              ObjectFactory, Referenceable
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
    private QpidURL _qpidURL;

    // Undefined at the moment
    public ConnectionFactoryImpl(QpidURL url)
    {
        _qpidURL = url;
    }

    public ConnectionFactoryImpl(String url) throws MalformedURLException
    {
        _qpidURL = new QpidURLImpl(url);
        BrokerDetails bd = _qpidURL.getAllBrokerDetails().get(0);
        _host = bd.getHost();
        _port = bd.getPort();
        _defaultUsername = bd.getUserName();
        _defaultPassword = bd.getPassword();
        _virtualHost = bd.getVirtualHost();
    }

    /**
     * Create a connection Factory
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

    /**
     * Creates an object using the location or reference information
     * specified.
     *
     * @param obj         The possibly null object containing location or reference
     *                    information that can be used in creating an object.
     * @param name        The name of this object relative to <code>nameCtx</code>,
     *                    or null if no name is specified.
     * @param nameCtx     The context relative to which the <code>name</code>
     *                    parameter is specified, or null if <code>name</code> is
     *                    relative to the default initial context.
     * @param environment The possibly null environment that is used in
     *                    creating the object.
     * @return The object created; null if an object cannot be created.
     * @throws Exception if this object factory encountered an exception
     *                   while attempting to create an object, and no other object factories are
     *                   to be tried.
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable environment) throws Exception
    {
        if (obj instanceof Reference)
        {
            Reference ref = (Reference) obj;

            if (ref.getClassName().equals(QueueImpl.class.getName()))
            {
                RefAddr addr = ref.get(QueueImpl.class.getName());

                if (addr != null)
                {
                    return new QueueImpl(new BindingURLImpl((String) addr.getContent()));
                }
            }

            if (ref.getClassName().equals(TopicImpl.class.getName()))
            {
                RefAddr addr = ref.get(TopicImpl.class.getName());

                if (addr != null)
                {
                    return new TopicImpl(new BindingURLImpl((String) addr.getContent()));
                }
            }

            if (ref.getClassName().equals(DestinationImpl.class.getName()))
            {
                RefAddr addr = ref.get(DestinationImpl.class.getName());

                if (addr != null)
                {
                    return new DestinationImpl(new BindingURLImpl((String) addr.getContent()));
                }
            }

            if (ref.getClassName().equals(ConnectionFactoryImpl.class.getName()))
            {
                RefAddr addr = ref.get(ConnectionFactoryImpl.class.getName());
                if (addr != null)
                {
                    return new ConnectionFactoryImpl(new QpidURLImpl((String) addr.getContent()));
                }
            }

        }
        return null;
    }

    //-- interface Reference
    /**
     * Retrieves the Reference of this object.
     *
     * @return The non-null Reference of this object.
     * @throws NamingException If a naming exception was encountered while retrieving the reference.
     */
    public Reference getReference() throws NamingException
    {
        return new Reference(ConnectionFactoryImpl.class.getName(),
                             new StringRefAddr(ConnectionFactoryImpl.class.getName(), _qpidURL.getURL()),
                             ConnectionFactoryImpl.class.getName(), null);
    }
}
