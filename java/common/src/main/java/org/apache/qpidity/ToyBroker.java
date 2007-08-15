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

import static org.apache.qpidity.Functions.str;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * ToyBroker
 *
 * @author Rafael H. Schloming
 */

class ToyBroker extends SessionDelegate
{

    private ToyExchange exchange;
    private MessageTransfer xfr = null;
    private DeliveryProperties props = null;
    private Struct[] headers = null;
    private List<Frame> frames = null;
    private Map<String,String> consumers = new HashMap<String,String>();
    
    public ToyBroker(ToyExchange exchange)
    {
        this.exchange = exchange;
    }

    @Override public void queueDeclare(Session ssn, QueueDeclare qd)
    {
        exchange.createQueue(qd.getQueue());
        System.out.println("\n==================> declared queue: " + qd.getQueue() + "\n");
    }
    
    @Override public void queueBind(Session ssn, QueueBind qb)
    {
        exchange.bindQueue(qb.getExchange(), qb.getRoutingKey(),qb.getQueue());
        System.out.println("\n==================> bound queue: " + qb.getQueue() + " with routing key " + qb.getRoutingKey() + "\n");
    }

    @Override public void queueQuery(Session ssn, QueueQuery qq)
    {
        QueueQueryResult result = new QueueQueryResult().queue(qq.getQueue());
        ssn.executionResult(qq.getId(), result);
    }
    
    @Override public void messageSubscribe(Session ssn, MessageSubscribe ms)
    {
        consumers.put(ms.getDestination(),ms.getQueue());
        System.out.println("\n==================> message subscribe : " + ms.getDestination() + "\n");
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
            Message m = new Message(headers, frames);
            
            if (exchange.route(dest,props.getRoutingKey(),m))
            {
                System.out.println("queued " + m);
                dispatchMessages(ssn);
            }
            else
            {
                
                reject(ssn);
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
    
    private void transferMessage(Session ssn,String dest, Message m)
    {
        System.out.println("\n==================> Transfering message to: " +dest + "\n");
        ssn.messageTransfer(dest, (short)0, (short)0);
        ssn.headers(m.headers);
        for (Frame f : m.frames)
        {
            for (ByteBuffer b : f)
            {
                ssn.data(b);
            }
        }
        ssn.endData();
    }
    
    public void dispatchMessages(Session ssn)
    {
        for (String dest: consumers.keySet())
        {
            Message m = exchange.getQueue(consumers.get(dest)).poll();
            if(m != null)
            {
                transferMessage(ssn,dest,m);
            }
        }
    }

    class Message
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
        final ToyExchange exchange = new ToyExchange();        
        ConnectionDelegate delegate = new ConnectionDelegate()
        {
            public SessionDelegate getSessionDelegate()
            {
                return new ToyBroker(exchange);
            }
        };
        
        //hack
        delegate.setUsername("guest");
        delegate.setPassword("guest");
        
        MinaHandler.accept("0.0.0.0", 5672, delegate);
    }

}
