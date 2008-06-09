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
package org.apache.qpidity.transport;

import org.apache.qpidity.transport.network.Frame;

/**
 * Method
 *
 * @author Rafael H. Schloming
 */

public abstract class Method extends Struct implements ProtocolEvent
{

    public static final Method create(int type)
    {
        // XXX: should generate separate factories for separate
        // namespaces
        return (Method) StructFactory.createInstruction(type);
    }

    // XXX: command subclass?
    private int id;
    private boolean idSet = false;
    private boolean sync = false;

    public final int getId()
    {
        return id;
    }

    void setId(int id)
    {
        this.id = id;
        this.idSet = true;
    }

    public final boolean isSync()
    {
        return sync;
    }

    void setSync(boolean value)
    {
        this.sync = value;
    }

    public abstract boolean hasPayload();

    public abstract byte getEncodedTrack();

    public abstract <C> void dispatch(C context, MethodDelegate<C> delegate);

    public <C> void delegate(C context, ProtocolDelegate<C> delegate)
    {
        if (getEncodedTrack() == Frame.L4)
        {
            delegate.command(context, this);
        }
        else
        {
            delegate.control(context, this);
        }
    }

    public String toString()
    {
        if (getEncodedTrack() != Frame.L4)
        {
            return super.toString();
        }

        StringBuilder str = new StringBuilder();

        if (idSet)
        {
            str.append("id=");
            str.append(id);
        }

        if (sync)
        {
            if (str.length() > 0)
            {
                str.append(" ");
            }
            str.append(" [sync]");
        }

        if (str.length() > 0)
        {
            str.append(" ");
        }
        str.append(super.toString());
        return str.toString();
    }

}
