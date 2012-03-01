package org.apache.qpid.ra.tm;

import javax.naming.InitialContext;
import javax.transaction.TransactionManager;

public class JBoss7TransactionManagerLocator
{
    private static final String TM_JNDI_NAME = "java:jboss/TransactionManager";

    public TransactionManager getTm() throws Exception
    {
        InitialContext ctx = null;

        try
        {
            ctx = new InitialContext();
            return (TransactionManager)ctx.lookup(TM_JNDI_NAME);
        }
        finally
        {
            try
            {
                if(ctx != null)
                {
                    ctx.close();
                }
            }
            catch(Exception ignore)
            {
            }
        }
    }
}
