package org.apache.qpidity.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.apache.qpidity.QpidException;

public class ConnectionFactoryImpl implements ConnectionFactory,QueueConnectionFactory, TopicConnectionFactory, Referenceable
{   
    private String _host;
    private int _port;
    private String _defaultUsername;
    private String _defaultPassword;
    private String _virtualPath;
    private String _url;
    
    // Undefined at the moment
    public ConnectionFactoryImpl(String url)
    {
        _url = url;
    }
    
    public ConnectionFactoryImpl(String host,int port,String virtualHost,String defaultUsername,String defaultPassword)
    {
        _host = host;
        _port = port;
        _defaultUsername = defaultUsername;
        _defaultPassword = defaultPassword;
        _virtualPath = virtualHost;    
    }
    
    public Connection createConnection() throws JMSException
    {   
        try
        {
            return new ConnectionImpl(_host,_port,_virtualPath,_defaultUsername,_defaultPassword);
        }
        catch(QpidException e)
        {
            // need to convert the qpid exception into jms exception
            throw new JMSException("","");
        }
    }

    public Connection createConnection(String username, String password) throws JMSException
    {
        try
        {
            return new ConnectionImpl(_host,_port,_virtualPath,username,password);
        }
        catch(QpidException e)
        {
            // need to convert the qpid exception into jms exception
            throw new JMSException("","");
        }
    }

    // ----------------------------------------
    // Support for JMS 1.0 classes
    // ----------------------------------------
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
    
    
    // ----------------------------------------
    // Support for JNDI
    // ----------------------------------------
    public Reference getReference() throws NamingException
    {
        return new Reference( ConnectionFactoryImpl.class.getName(),
                new StringRefAddr(ConnectionFactoryImpl.class.getName(),_url));
    }

}
