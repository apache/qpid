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

import org.apache.mina.common.IoSession;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Buffers any received messages until join completes.
 *
 */
class InductionBuffer
{
    private final Queue<Message> _buffer = new LinkedList<Message>();
    private final MessageHandler _handler;
    private boolean _buffering = true;

    InductionBuffer(MessageHandler handler)
    {
        _handler = handler;
    }

    private void process() throws Exception
    {
        for (Message o = _buffer.poll(); o != null; o = _buffer.poll())
        {
            o.deliver(_handler);
        }
        _buffering = false;
    }

    synchronized void deliver() throws Exception
    {
        process();
    }

    synchronized void receive(IoSession session, Object msg) throws Exception
    {
        if (_buffering)
        {
            _buffer.offer(new Message(session, msg));
        }
        else
        {
            _handler.deliver(session, msg);
        }
    }

    private static class Message
    {
        private final IoSession _session;
        private final Object _msg;

        Message(IoSession session, Object msg)
        {
            _session = session;
            _msg = msg;
        }

        void deliver(MessageHandler handler) throws Exception
        {
            handler.deliver(_session, _msg);
        }
    }

    static interface MessageHandler
    {
        public void deliver(IoSession session, Object msg) throws Exception;
    }
}
