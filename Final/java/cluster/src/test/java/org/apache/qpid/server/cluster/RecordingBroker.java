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

import java.util.ArrayList;
import java.util.List;

class RecordingBroker extends TestBroker
{
    private final List<AMQDataBlock> _messages = new ArrayList<AMQDataBlock>();

    RecordingBroker(String host, int port)
    {
        super(host, port);
    }

    public void send(AMQDataBlock data) throws AMQException
    {
        _messages.add(data);
    }

    List<AMQDataBlock> getMessages()
    {
        return _messages;
    }

    void clear()
    {
        _messages.clear();
    }

}
