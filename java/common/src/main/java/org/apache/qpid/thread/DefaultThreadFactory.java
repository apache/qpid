package org.apache.qpid.thread;

public class DefaultThreadFactory implements ThreadFactory
{

    private static class QpidThread extends Thread
    {
        private QpidThread(final Runnable target)
        {
            super(target);
        }

    }



    public Thread createThread(Runnable r)
    {
        return new Thread(r);
    }

    public Thread createThread(Runnable r, int priority)
    {
        Thread t = new Thread(r);
        t.setPriority(priority);
        return t;
    }

}
