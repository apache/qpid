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

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.apache.qpidity.Functions.*;


/**
 * ToyBroker
 *
 * @author Rafael H. Schloming
 */

class ToyBroker extends SessionDelegate
{

    private Map<String,Queue<Message>> queues;
    private MessageTransfer xfr = null;
    private DeliveryProperties props = null;
    private Struct[] headers = null;
    private List<Frame> frames = null;

    public ToyBroker(Map<String,Queue<Message>> queues)
    {
        this.queues = queues;
    }

    @Override public void queueDeclare(Session ssn, QueueDeclare qd)
    {
        queues.put(qd.getQueue(), new LinkedList());
        System.out.println("declared queue: " + qd.getQueue());
    }

    @Override public void queueQuery(Session ssn, QueueQuery qq)
    {
        QueueQueryResult result = new QueueQueryResult().queue(qq.getQueue());
        ssn.executionResult(qq.getId(), result);
    }

    @Override public void messageTransfer(Session ssn, MessageTransfer xfr)
    {
        this.xfr = xfr;
        frames = new ArrayList();
    }

    public void headers(Session ssn, Struct ... headers)
    {
        if (xfr == null || frames == null)
        {
            ssn.connectionClose(503, "no method segment", 0, 0);
            // XXX: close at our end
            return;
        }

        for (Struct hdr : headers)
        {
            if (hdr instanceof DeliveryProperties)
            {
                props = (DeliveryProperties) hdr;
            }
        }

        if (props != null && !props.getDiscardUnroutable())
        {
            String dest = xfr.getDestination();
            if (!queues.containsKey(dest))
            {
                reject(ssn);
            }
        }

        this.headers = headers;
    }

    public void data(Session ssn, Frame frame)
    {
        if (xfr == null || frames == null)
        {
            ssn.connectionClose(503, "no method segment", 0, 0);
            // XXX: close at our end
            return;
        }

        frames.add(frame);

        if (frame.isLastSegment() && frame.isLastFrame())
        {
            String dest = xfr.getDestination();
            Queue queue = queues.get(dest);
            if (queue == null)
            {
                reject(ssn);
            }
            else
            {
                Message m = new Message(headers, frames);
                queue.offer(m);
                System.out.println("queued " + m);
            }
            ssn.processed(xfr);
            xfr = null;
            frames = null;
        }
    }

    private void reject(Session ssn)
    {
        if (props != null && props.getDiscardUnroutable())
        {
            return;
        }
        else
        {
            RangeSet ranges = new RangeSet();
            ranges.add(xfr.getId());
            ssn.messageReject(ranges, 0, "no such destination");
        }
    }

    private class Message
    {
        private final Struct[] headers;
        private final List<Frame> frames;

        public Message(Struct[] headers, List<Frame> frames)
        {
            this.headers = headers;
            this.frames = frames;
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            if (headers != null)
            {
                boolean first = true;
                for (Struct hdr : headers)
                {
                    if (first) { first = false; }
                    else { sb.append(" "); }
                    sb.append(hdr);
                }
            }

            for (Frame f : frames)
            {
                for (ByteBuffer b : f)
                {
                    sb.append(" | ");
                    sb.append(str(b));
                }
            }

            return sb.toString();
        }

    }

    public static final void main(String[] args) throws IOException
    {
        final Map<String,Queue<Message>> queues =
            new HashMap<String,Queue<Message>>();
        
        ConnectionDelegate delegate = new ConnectionDelegate()
        {
            public SessionDelegate getSessionDelegate()
            {
                return new ToyBroker(queues);
            }
        };
        
        //hack
        delegate.setUsername("guest");
        delegate.setPassword("guest");
        
        MinaHandler.accept("0.0.0.0", 5672, delegate);
    }

}
