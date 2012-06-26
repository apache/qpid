package org.apache.qpid.messaging.util.failover;

import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.FailoverStrategy;
import org.apache.qpid.messaging.internal.FailoverStrategyFactory;

public class DefaultFailoverStrategyFactory extends FailoverStrategyFactory
{

    @Override
    public FailoverStrategy getFailoverStrategy(ConnectionInternal con)
    {
        return new DefaultFailoverStrategy(con.getConnectionURL(), con.getConnectionOptions());
    }
}
