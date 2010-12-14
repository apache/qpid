package org.apache.qpid.perftests.dlq.client;

import static org.apache.qpid.perftests.dlq.client.Config.*;

import java.util.Properties;
import java.util.concurrent.Callable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.configuration.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent abstract class for all performance test clients that connect to a
 * broker and perform test operations. All clients are {@link Callable}
 * objects that return an integer value, or throw an exception. The
 * {@link #connect()} method returns a boolean to indicate whether the
 * broker connection succeeded, and can be used to abort tests if there is
 * no available broker.
 */
public abstract class Client implements Callable<Integer>
{
    protected static final Logger _log = LoggerFactory.getLogger(Client.class);
    
    protected Properties _props;
    
    protected String _broker;
    protected int _maxRedelivery;
    protected int _maxPrefetch;
    protected int _sessionType;
    protected boolean _transacted;
    protected boolean _clientAck;
    protected String _queueName;
    protected int _count;
    protected boolean _messageIdsDisabled;
    protected boolean _persistent;
    protected int _size;
    protected int _threads;
    protected int _maxRecords;
    protected boolean _dlq;
    
    protected Connection _connection;
    protected Session _session;
    protected Destination _queue;
    protected String _client = "client";
    
    public Client(Properties props)
    {
        _props = props;
        
        init();
    }
    
    public void init()
    {
        _broker = _props.getProperty(BROKER);
        _maxRedelivery = Integer.parseInt(_props.getProperty(MAX_REDELIVERY));
        _maxPrefetch = Integer.parseInt(_props.getProperty(MAX_PREFETCH));
        _sessionType = getSessionType(_props.getProperty(SESSION));
        _transacted = _sessionType == Session.SESSION_TRANSACTED;
        _clientAck = _sessionType == Session.CLIENT_ACKNOWLEDGE;
        _queueName = _props.getProperty(QUEUE);
        _persistent = Boolean.parseBoolean(_props.getProperty(PERSISTENT));
        _count = Integer.parseInt(_props.getProperty(COUNT));
        _size = Integer.parseInt(_props.getProperty(SIZE));
        _messageIdsDisabled = !Boolean.parseBoolean(_props.getProperty(MESSAGE_IDS));
        _threads = Integer.parseInt(_props.getProperty(THREADS));
        _maxRecords = Integer.parseInt(_props.getProperty(MAX_RECORDS));
        _dlq = Boolean.parseBoolean(_props.getProperty(DLQ));
    }
    
    public void shutdown()
    {
        try
        {
            _connection.close();
        }
        catch (JMSException e)
        {
            _log.error("failed shutting down the connection", e);
        }
    }
    
    public int getSessionType(String sessionType)
    {
        if (sessionType == null || sessionType.length() == 0)
        {
            throw new RuntimeException("empty or missing session property");
        }
        else if (sessionType.equalsIgnoreCase(SESSION_TRANSACTED))
        {
            return Session.SESSION_TRANSACTED;
        }
        else if (sessionType.equalsIgnoreCase(AUTO_ACKNOWLEDGE))
        {
            return Session.AUTO_ACKNOWLEDGE;
        }
        else if (sessionType.equalsIgnoreCase(CLIENT_ACKNOWLEDGE))
        {
            return Session.CLIENT_ACKNOWLEDGE;
        }
        else if (sessionType.equalsIgnoreCase(DUPS_OK_ACKNOWLEDGE))
        {
            return Session.DUPS_OK_ACKNOWLEDGE;
        }
        throw new RuntimeException("session property not recognised: " + sessionType);
    }
    
    public boolean connect()
    {
        String url = "amqp://guest:guest@" + _client + "/test?brokerlist='" + _broker + "'&maxprefetch='" + _maxPrefetch + "'&maxdeliverycount='" + _maxRedelivery + "'";
        System.setProperty(ClientProperties.MAX_DELIVERY_RECORDS_PROP_NAME, Integer.toString(_maxRecords));
        
        try
        {
            _connection = new AMQConnection(url);
            _session = _connection.createSession(_transacted, _sessionType);
            _queue = _session.createQueue(_queueName);
            _connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException e)
                {
                    _log.error("jms exception received", e);
                    System.exit(0);
                }
            });
            return true;
        }
        catch (Exception e)
        {
            _log.error("Unable to setup connection, client and producer on broker", e);
            return false;
        }
    }
    
    public abstract void start() throws Exception;
    
    public Integer call() throws Exception {
        start();
        return -1;
    }
}
