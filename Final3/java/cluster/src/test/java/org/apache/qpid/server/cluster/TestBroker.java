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
package org.apache.qpid.server.cluster;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;

import java.io.IOException;

class TestBroker extends Broker
{
    TestBroker(String host, int port)
    {
        super(host, port);
    }

    boolean connect() throws IOException, InterruptedException
    {
        return true;
    }

    void connectAsynch(Iterable<AMQMethodBody> msgs)
    {
        replay(msgs);
    }

    void replay(Iterable<AMQMethodBody> msgs)
    {
        try
        {
            for (AMQMethodBody b : msgs)
            {
                send(new AMQFrame(0, b));
            }
        }
        catch (AMQException e)
        {
            throw new RuntimeException(e);
        }
    }

    Broker connectToCluster() throws IOException, InterruptedException
    {
        return this;
    }

    public void send(AMQDataBlock data) throws AMQException
    {
    }
}
