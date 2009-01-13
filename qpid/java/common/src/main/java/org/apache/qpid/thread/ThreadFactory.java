package org.apache.qpid.thread;

public interface ThreadFactory
{
    public Thread createThread(Runnable r) throws Exception;
    public Thread createThread(Runnable r, int priority) throws Exception;
}
