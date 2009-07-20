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
package org.apache.qpid.server.transport;

import org.apache.qpid.transport.*;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.AMQException;

import java.util.ArrayList;

public class ServerSession extends Session
{
    ServerSession(Connection connection, Binary name, long expiry)
    {
        super(connection, name, expiry);
    }

    ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry)
    {
        super(connection, delegate, name, expiry);
    }

    public void enqueue(ServerMessage message, ArrayList<AMQQueue> queues)
    {
        // TODO Txn

        try
        {
            for(AMQQueue q : queues)
            {
                q.enqueue(message);
            }
        }
        catch (AMQException e)
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void sendMessage(MessageTransfer xfr)
    {
        invoke(xfr);
    }
}
