package org.apache.qpid.extras;

import org.apache.qpid.extras.exchanges.diagnostic.DiagnosticExchangeType;
import org.apache.qpid.extras.exchanges.example.TestExchangeType;
import org.apache.qpid.server.exchange.ExchangeType;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * 
 * @author aidan
 * 
 * Dummy class, used by PluginTest
 */

public class Activator implements BundleActivator
{

    public void start(BundleContext ctx) throws Exception
    {
        ctx.registerService(ExchangeType.class.getName(), new TestExchangeType(), null);
        ctx.registerService(ExchangeType.class.getName(), new DiagnosticExchangeType(), null);
    }

    public void stop(BundleContext ctx) throws Exception
    {
    }
}
