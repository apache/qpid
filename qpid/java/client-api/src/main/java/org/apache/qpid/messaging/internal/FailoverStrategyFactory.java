package org.apache.qpid.messaging.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FailoverStrategyFactory
{
    private final static FailoverStrategyFactory _instance;
    private static final Logger _logger = LoggerFactory.getLogger(FailoverStrategyFactory.class);

    static
    {
        String className = System.getProperty("qpid.failover-factory",
                "org.apache.qpid.messaging.util.failover.DefaultFailoverStrategyFactory"); // will default to java
        try
        {
            _instance = (FailoverStrategyFactory) Class.forName(className).newInstance();
        }
        catch (Exception e)
        {
            _logger.error("Error loading failover factory class",e);
            throw new Error("Error loading failover factory class",e);
        }
    }

    public static FailoverStrategyFactory get()
    {
        return _instance;
    }

    public abstract FailoverStrategy getFailoverStrategy(ConnectionInternal con);
    
}
