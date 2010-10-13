package org.apache.qpid.thread;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */

import java.lang.reflect.Constructor;
import java.util.concurrent.ThreadFactory;

public class RealtimeThreadFactory implements ThreadFactory
{
    private Class<?> _threadClass;
    private Constructor<?> _threadConstructor;
    private Constructor<?> _priorityParameterConstructor;
    private int _defaultRTThreadPriority = 20;
    
    public RealtimeThreadFactory() throws Exception
    {
        _defaultRTThreadPriority = Integer.getInteger("qpid.rt_thread_priority", 20);
        _threadClass = Class.forName("javax.realtime.RealtimeThread");
    
        Class<?> schedulingParametersClass = Class.forName("javax.realtime.SchedulingParameters");
        Class<?> releaseParametersClass = Class.forName("javax.realtime.ReleaseParameters");
        Class<?> memoryParametersClass = Class.forName("javax.realtime.MemoryParameters");
        Class<?> memoryAreaClass = Class.forName("javax.realtime.MemoryArea");
        Class<?> processingGroupParametersClass = Class.forName("javax.realtime.ProcessingGroupParameters");
     
        Class<?>[] paramTypes = new Class[] { schedulingParametersClass,
                                              releaseParametersClass, 
                                              memoryParametersClass,
                                              memoryAreaClass,
                                              processingGroupParametersClass,
                                              java.lang.Runnable.class };
        
        _threadConstructor = _threadClass.getConstructor(paramTypes);
        
        Class<?> priorityParameterClass = Class.forName("javax.realtime.PriorityParameters");
        _priorityParameterConstructor = priorityParameterClass.getConstructor(new Class<?>[] { Integer.TYPE });        
    }

    public Thread newThread(Runnable r)
    {
        return createThread(r,_defaultRTThreadPriority);
    }

    public Thread createThread(Runnable r, int priority)
    {
        try
        {
	        Object priorityParams = _priorityParameterConstructor.newInstance(priority);
	        return (Thread) _threadConstructor.newInstance(priorityParams, null, null, null, null, r);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
