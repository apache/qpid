package org.apache.qpid.thread;

import java.lang.reflect.Constructor;

public class RealtimeThreadFactory implements ThreadFactory
{
    private Class threadClass;
    private Constructor threadConstructor;
    private Constructor priorityParameterConstructor;
    private int defaultRTThreadPriority = 20;
    
    public RealtimeThreadFactory() throws Exception
    {
        defaultRTThreadPriority = Integer.getInteger("qpid.rt_thread_priority",20);
        threadClass = Class.forName("javax.realtime.RealtimeThread");
    
        Class schedulingParametersClass = Class.forName("javax.realtime.SchedulingParameters");
        Class releaseParametersClass = Class.forName("javax.realtime.ReleaseParameters");
        Class memoryParametersClass = Class.forName("javax.realtime.MemoryParameters");
        Class memoryAreaClass = Class.forName("javax.realtime.MemoryArea");
        Class processingGroupParametersClass = Class.forName("javax.realtime.ProcessingGroupParameters");
     
        Class[] paramTypes = new Class[]{schedulingParametersClass,
                                         releaseParametersClass, 
                                         memoryParametersClass,
                                         memoryAreaClass,
                                         processingGroupParametersClass,
                                         java.lang.Runnable.class};
        
        threadConstructor = threadClass.getConstructor(paramTypes);
        
        Class priorityParameterClass = Class.forName("javax.realtime.PriorityParameters");
        priorityParameterConstructor = priorityParameterClass.getConstructor(new Class[]{int.class});        
    }

    public Thread createThread(Runnable r) throws Exception
    {
        return createThread(r,defaultRTThreadPriority);
    }

    public Thread createThread(Runnable r, int priority) throws Exception
    {
        Object priorityParams = priorityParameterConstructor.newInstance(priority);
        return (Thread)threadConstructor.newInstance(priorityParams,null,null,null,null,r);
    }

}
