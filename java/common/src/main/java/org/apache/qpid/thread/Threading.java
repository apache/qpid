package org.apache.qpid.thread;

public final class Threading
{
    private static ThreadFactory threadFactory;
    
    static {
        try
        {
            Class threadFactoryClass = 
                Class.forName(System.getProperty("qpid.thread_factory", 
                                                 "org.apache.qpid.thread.DefaultThreadFactory"));
            
            threadFactory = (ThreadFactory)threadFactoryClass.newInstance();
        }
        catch(Exception e)
        {
            throw new Error("Error occured while loading thread factory",e);
        }
    }
    
    public static ThreadFactory getThreadFactory()
    {
        return threadFactory;
    }
}
