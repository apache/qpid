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

import java.util.HashMap;
import java.util.Map;

/**
 * Session
 *
 * @author Rafael H. Schloming
 */

public class Session extends Invoker implements DelegateResolver<Session>
{

    // channel may be null
    Channel channel;
    private int command_id = 0;
    // XXX
    final Map<Integer,Handler<Struct>> handlers = new HashMap<Integer,Handler<Struct>>();
    final private Delegate<Session> delegate = new SessionDelegate();

    public void attach(Channel channel)
    {
        this.channel = channel;
        channel.setSession(this);
    }

    protected void invoke(Method m)
    {
        command_id++;
        channel.write(m);
    }

    protected void invoke(Method m, Handler<Struct> handler)
    {
        invoke(m);
        handlers.put(command_id, handler);
    }

    protected StructFactory getFactory()
    {
        return channel.getFactory();
    }

    public Delegate<Session> resolve(Struct struct)
    {
        return delegate;
    }

}
