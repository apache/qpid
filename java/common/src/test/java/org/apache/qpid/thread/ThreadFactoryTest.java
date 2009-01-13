package org.apache.qpid.thread;

import junit.framework.TestCase;

public class ThreadFactoryTest extends TestCase
{
    public void testThreadFactory()
    {
        Class threadFactoryClass = null;
        try
        {
            threadFactoryClass = Class.forName(System.getProperty("qpid.thread_factory",
                    "org.apache.qpid.thread.DefaultThreadFactory"));            
        }
        // If the thread factory class was wrong it will flagged way before it gets here.
        catch(Exception e)
        {            
            fail("Invalid thread factory class");
        }
        
        assertEquals(threadFactoryClass, Threading.getThreadFactory().getClass());
    }
    
    public void testThreadCreate()
    {
        Runnable r = new Runnable(){
          
            public void run(){
                
            }            
        };
        
        Thread t = null;
        try
        {
            t = Threading.getThreadFactory().createThread(r,5);
        }
        catch(Exception e)
        {
            fail("Error creating thread using Qpid thread factory");
        }
        
        assertNotNull(t);
        assertEquals(5,t.getPriority());
    }
}
