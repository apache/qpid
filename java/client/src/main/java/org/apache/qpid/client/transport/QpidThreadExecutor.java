package org.apache.qpid.client.transport;

import org.apache.qpid.thread.Threading;

import edu.emory.mathcs.backport.java.util.concurrent.Executor;

public class QpidThreadExecutor implements Executor
{
    @Override
    public void execute(Runnable command)
    {
        try
        {
            Threading.getThreadFactory().createThread(command).start();
        }
        catch(Exception e)
        {
            throw new RuntimeException("Error creating a thread using Qpid thread factory",e);
        }
    }

}
