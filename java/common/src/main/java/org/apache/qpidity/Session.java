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
package org.apache.qpidity;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;


/**
 * Session
 *
 * @author Rafael H. Schloming
 */

public class Session extends Invoker
{

    // channel may be null
    Channel channel;

    // incoming command count
    private long commandsIn = 0;
    // completed incoming commands
    private final RangeSet processed = new RangeSet();
    private Range syncPoint = null;
    
    // outgoing command count
    private long commandsOut = 0;
    private Map<Long,Method> commands = new HashMap<Long,Method>();
    private long mark = 0;
    

    public Map<Long,Method> getOutstandingCommands()
    {
        return commands;
    }

    public long getCommandsOut()
    {
        return commandsOut;
    }

    public long getCommandsIn()
    {
        return commandsIn;
    }

    public long nextCommandId()
    {
        return commandsIn++;
    }

    public RangeSet getProcessed()
    {
        return processed;
    }

    public void processed(Method command)
    {
        processed(command.getId());
    }

    public void processed(long command)
    {
        processed(new Range(command, command));
    }

    public void processed(long lower, long upper)
    {
        processed(new Range(lower, upper));
    }

    public void processed(Range range)
    {
        boolean flush;
        synchronized (processed)
        {
            processed.add(range);
            flush = syncPoint != null && processed.includes(syncPoint);
        }
        if (flush)
        {
            flushProcessed();
        }
    }

    void flushProcessed()
    {           
        for (Range r: processed)
        {
            System.out.println("Completed Range [" + r.getLower() + "," + r.getUpper() +"]" );     
        }
        System.out.println("Notifying peer with execution complete");        
        executionComplete(0, processed);
    }

    void syncPoint()
    {
        System.out.println("===========Request received to sync==========================");
        
        Range range = new Range(0, getCommandsIn() - 1);
        boolean flush;
        synchronized (processed)
        {
            flush = processed.includes(range);
            if (!flush)
            {
                syncPoint = range;
            }
        }
        if (flush)
        {
            flushProcessed();
        }
    }

    public void attach(Channel channel)
    {
        this.channel = channel;
        channel.setSession(this);
    }

    public Method getCommand(long id)
    {
        synchronized (commands)
        {
            return commands.get(id);
        }
    }

    void complete(long lower, long upper)
    {
        synchronized (commands)
        {
            for (long id = lower; id <= upper; id++)
            {
                commands.remove(id);
            }  
             
            if (commands.isEmpty())
            {
                System.out.println("\n All outstanding commands are completed !!!! \n");
                commands.notifyAll();
            }
        }
    }

    void complete(long mark)
    {
        complete(this.mark, mark);
        this.mark = mark;
    }

    protected void invoke(Method m)
    {
        if (m.getEncodedTrack() == Frame.L4)
        {
            synchronized (commands)
            {
                System.out.println("sent command " + m.getClass().getName() + " command Id" + commandsOut);
                commands.put(commandsOut++, m);                
            }
        }
        channel.method(m);
    }

    public void headers(Struct ... headers)
    {
        channel.headers(headers);
    }

    public void data(ByteBuffer buf)
    {
        channel.data(buf);
    }

    public void data(String str)
    {
        channel.data(str);
    }

    public void data(byte[] bytes)
    {
        channel.data(bytes);
    }

    public void endData()
    {
        channel.end();
    }

    public void sync()
    {
        System.out.println("calling sync()"); 
        synchronized (commands)
        {
            if (!commands.isEmpty())
            {
                executionSync();
            }

            while (!commands.isEmpty())
            {
                try {
                    System.out.println("\n============sync() waiting for commmands to be completed ==============\n"); 
                    commands.wait();
                    System.out.println("\n============sync() got notified=========================================\n");
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Map<Long,ResultFuture<?>> results =
        new HashMap<Long,ResultFuture<?>>();

    void result(long command, Struct result)
    {
        ResultFuture<?> future;
        synchronized (results)
        {
            future = results.remove(command);
        }
        future.set(result);
    }
    protected <T> Future<T> invoke(Method m, Class<T> klass)
    {
        long command = commandsOut;
        ResultFuture<T> future = new ResultFuture<T>(klass);
        synchronized (results)
        {
            results.put(command, future);
        }
        invoke(m);
        return future;
    }

    private class ResultFuture<T> implements Future<T>
    {

        private final Class<T> klass;
        private T result;

        private ResultFuture(Class<T> klass)
        {
            this.klass = klass;
        }

        private void set(Struct result)
        {
            synchronized (this)
            {
                this.result = klass.cast(result);
                notifyAll();
            }
        }

        public T get(long timeout, int nanos)
        {
            synchronized (this)
            {
                while (!isDone())
                {
                    try
                    {
                        wait(timeout, nanos);
                    }
                    catch (InterruptedException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }

            return result;
        }

        public T get(long timeout)
        {
            return get(timeout, 0);
        }

        public T get()
        {
            return get(0);
        }

        public boolean isDone()
        {
            return result != null;
        }

    }

}
