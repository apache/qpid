package org.apache.qpid.client.perf;

import javax.naming.InitialContext;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionUtility
{
    private static final Logger _logger = LoggerFactory.getLogger(ConnectionUtility.class);

    private InitialContext _initialContext;
    private AMQConnectionFactory _connectionFactory;

    private static ConnectionUtility _instance = new ConnectionUtility();

    public static ConnectionUtility getInstance()
    {
        return _instance;
    }

    private InitialContext getInitialContext() throws Exception
    {
        _logger.info("get InitialContext");
        if (_initialContext == null)
        {
            _initialContext = new InitialContext();
        }
        return _initialContext;
    }

    private AMQConnectionFactory getConnectionFactory() throws Exception
    {
        _logger.info("get ConnectionFactory");
        if (_connectionFactory == null)
        {
            _connectionFactory = (AMQConnectionFactory) getInitialContext().lookup("local");
        }
        return _connectionFactory;
    }

    public AMQConnection getConnection() throws Exception
    {
        _logger.info("get Connection");
        return (AMQConnection)getConnectionFactory().createConnection();
    }

}
