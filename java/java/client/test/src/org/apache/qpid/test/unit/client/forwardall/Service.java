/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.test.unit.client.forwardall;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.JMSException;

/**
 * Declare a queue and bind it to amq.direct with a 'well known' routing key,
 * register a consumer for this queue and send a response to every message received.
 */
public class Service implements MessageListener
{
    private final AMQConnection _connection;
    private final AMQSession _session;

    Service(String broker) throws Exception
    {
        this(connect(broker));
    }

    Service(AMQConnection connection) throws Exception
    {
        _connection = connection;
        AMQQueue queue = new SpecialQueue("ServiceQueue");
        _session = (AMQSession) _connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _session.createConsumer(queue).setMessageListener(this);
        _connection.start();
    }

    public void onMessage(Message request)
    {
        try
        {
            Message response = _session.createTextMessage("Response!");
            Destination replyTo = request.getJMSReplyTo();
            _session.createProducer(replyTo).send(response);
        }
        catch (Exception e)
        {
            e.printStackTrace(System.out);
        }
    }

    public void close() throws JMSException
    {
        _connection.close();
    }

    static AMQConnection connect(String broker) throws Exception
    {
        return new AMQConnection(broker, "guest", "guest", "Client" + System.currentTimeMillis(), "/test_path");
    }

//    public static void main(String[] argv) throws Exception
//    {
//        String broker = argv.length == 0? "localhost:5672" : argv[0];
//        new Service(broker);
//    }
}
