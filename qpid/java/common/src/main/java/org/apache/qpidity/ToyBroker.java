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

import org.apache.qpidity.transport.*;
import org.apache.qpidity.transport.network.mina.MinaHandler;

import static org.apache.qpidity.transport.util.Functions.str;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


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
    private Header header = null;
    private List<Data> body = null;
    private Map<String,Consumer> consumers = new ConcurrentHashMap<String,Consumer>();

    public ToyBroker(ToyExchange exchange)
    {
        this.exchange = exchange;
    }

    public void messageAcquire(Session context, MessageAcquire struct)
    {
        System.out.println("\n==================> messageAcquire " );
        context.messageAcquired(struct.getTransfers());
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
        Consumer c = new Consumer();
        c._queueName = ms.getQueue();
        consumers.put(ms.getDestination(),c);
        System.out.println("\n==================> message subscribe : " + ms.getDestination() + " queue: " + ms.getQueue()  + "\n");
    }

    @Override public void messageFlow(Session ssn,MessageFlow struct)
    {
        Consumer c = consumers.get(struct.getDestination());
        c._credit = struct.getValue();
        System.out.println("\n==================> message flow : " + struct.getDestination() + " credit: " + struct.getValue()  + "\n");
    }

    @Override public void messageFlush(Session ssn,MessageFlush struct)
    {
        System.out.println("\n==================> message flush for consumer : " + struct.getDestination() + "\n");
        checkAndSendMessagesToConsumer(ssn,struct.getDestination());
    }

    @Override public void messageTransfer(Session ssn, MessageTransfer xfr)
    {
        this.xfr = xfr;
        body = new ArrayList<Data>();
        System.out.println("received transfer " + xfr.getDestination());
    }

    @Override public void header(Session ssn, Header header)
    {
        if (xfr == null || body == null)
        {
            ssn.connectionClose(503, "no method segment", 0, 0);
            ssn.close();
            return;
        }

        props = header.get(DeliveryProperties.class);
        if (props != null)
        {
            System.out.println("received headers routing_key " + props.getRoutingKey());
        }
        MessageProperties mp = header.get(MessageProperties.class);
        System.out.println("MP: " + mp);
        if (mp != null)
        {
            System.out.println(mp.getApplicationHeaders());
        }

        this.header = header;
    }

    @Override public void data(Session ssn, Data data)
    {
        if (xfr == null || body == null)
        {
            ssn.connectionClose(503, "no method segment", 0, 0);
            ssn.close();
            return;
        }

        body.add(data);

        if (data.isLast())
        {
            String dest = xfr.getDestination();
            Message m = new Message(header, body);

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
            body = null;
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

    private void transferMessageToPeer(Session ssn,String dest, Message m)
    {
        System.out.println("\n==================> Transfering message to: " +dest + "\n");
        ssn.messageTransfer(dest, (short)0, (short)0);
        ssn.header(m.header);
        for (Data d : m.body)
        {
            for (ByteBuffer b : d.getFragments())
            {
                ssn.data(b);
            }
        }
        ssn.endData();
    }

    private void dispatchMessages(Session ssn)
    {
        for (String dest: consumers.keySet())
        {
            checkAndSendMessagesToConsumer(ssn,dest);
        }
    }

    private void checkAndSendMessagesToConsumer(Session ssn,String dest)
    {
        Consumer c = consumers.get(dest);
        LinkedBlockingQueue<Message> queue = exchange.getQueue(c._queueName);
        Message m = queue.poll();
        while (m != null && c._credit>0)
        {
            transferMessageToPeer(ssn,dest,m);
            c._credit--;
            m = queue.poll();
        }
    }

    class Message
    {
        private final Header header;
        private final List<Data> body;

        public Message(Header header, List<Data> body)
        {
            this.header = header;
            this.body = body;
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            if (header != null)
            {
                boolean first = true;
                for (Struct st : header.getStructs())
                {
                    if (first) { first = false; }
                    else { sb.append(" "); }
                    sb.append(st);
                }
            }

            for (Data d : body)
            {
                for (ByteBuffer b : d.getFragments())
                {
                    sb.append(" | ");
                    sb.append(str(b));
                }
            }

            return sb.toString();
        }

    }

    // ugly, but who cares :)
    // assumes unit is always no of messages, not bytes
    // assumes it's credit mode and not window
    private class Consumer
    {
        long _credit;
        String _queueName;
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
            public void exception(Throwable t)
            {
                t.printStackTrace();
            }
            public void closed() {}
        };

        //hack
        delegate.setUsername("guest");
        delegate.setPassword("guest");

        MinaHandler.accept("0.0.0.0", 5672, delegate);
    }

}
