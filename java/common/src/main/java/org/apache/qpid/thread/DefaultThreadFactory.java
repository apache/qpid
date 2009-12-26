package org.apache.qpid.thread;

public class DefaultThreadFactory implements ThreadFactory
{

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
