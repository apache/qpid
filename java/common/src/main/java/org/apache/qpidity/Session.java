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

import org.apache.qpidity.api.Message;
/**
 * Session
 *
 * @author Rafael H. Schloming
 */

public class Session extends Invoker
{

    // channel may be null
    Channel channel;
    // outgoing command count
    private long commandsOut = 0;
    // XXX: incoming command count not used
    // incoming command count
    private long commandsIn = 0;
    private Map<Long,Method> commands = new HashMap<Long,Method>();
    private long mark = 0;

    public long getCommandsOut()
    {
        return commandsOut;
    }

    public long getCommandsIn()
    {
        return commandsIn;
    }

    public void attach(Channel channel)
    {
        this.channel = channel;
        channel.setSession(this);
    }

    public Method getCommand(long id)
    {
        System.out.println(id + " " + commands);
        return commands.get(id);
    }

    void complete(long lower, long upper)
    {
        for (long id = lower; id <= upper; id++)
        {
            commands.put(id, null);
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
            long cmd = commandsOut++;
            commands.put(cmd, m);
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

    public void end()
    {
        channel.end();
    }

    protected void invoke(Method m, Handler<Struct> handler)
    {
        throw new UnsupportedOperationException();
    }

}
