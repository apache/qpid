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
package org.apache.qpid.test.unit.client.message;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.After;

import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Message;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import java.io.Serializable;
import java.util.HashMap;
import java.util.ArrayList;

import junit.framework.JUnit4TestAdapter;

public class ObjectMessageTest implements MessageListener
{
    private final AMQConnection connection;
    private final AMQDestination destination;
    private final AMQSession session;
    private final Serializable[] data;
    private volatile boolean waiting;
    private int received;
    private final ArrayList items = new ArrayList();


    @Before
    public void createVMBroker()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create broker: " + e);
        }
    }

    @After
    public void stopVmBroker()
    {
        TransportConnection.killVMBroker(1);
    }

    ObjectMessageTest(String broker) throws Exception
    {
        this(new AMQConnection(broker, "guest", "guest", randomize("Client"), "/test_path"));
    }

    ObjectMessageTest(AMQConnection connection) throws Exception
    {
        this(connection, new AMQQueue(randomize("LatencyTest"), true));
    }

    ObjectMessageTest(AMQConnection connection, AMQDestination destination) throws Exception
    {
        this.connection = connection;
        this.destination = destination;
        session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        A a1 = new A(1, "A");
        A a2 = new A(2, "a");
        B b = new B(1, "B");
        C c = new C();
        c.put("A1", a1);
        c.put("a2", a2);
        c.put("B", b);
        c.put("String", "String");

        data = new Serializable[]{a1, a2, b, c, "Hello World!", new Integer(1001)};
    }


    public void test() throws Exception
    {
        try
        {
            send();
            waitUntilReceived(data.length);
            check();
            System.out.println("All " + data.length + " items matched.");
        }
        finally
        {
            close();
        }
    }

    private void send() throws Exception
    {
        //set up a consumer
        session.createConsumer(destination).setMessageListener(this);
        connection.start();

        //create a publisher
        MessageProducer producer = session.createProducer(destination, false, false, true);


        for (int i = 0; i < data.length; i++)
        {
            ObjectMessage msg;
            if (i % 2 == 0)
            {
                msg = session.createObjectMessage(data[i]);
            }
            else
            {
                msg = session.createObjectMessage();
                msg.setObject(data[i]);
            }
            producer.send(msg);
        }
    }

    public void check() throws Exception
    {
        Object[] actual = (Object[]) items.toArray();
        if (actual.length != data.length)
        {
            throw new Exception("Expected " + data.length + " objects, got " + actual.length);
        }
        for (int i = 0; i < data.length; i++)
        {
            if (actual[i] instanceof Exception)
            {
                throw new Exception("Error on receive of " + data[i], ((Exception) actual[i]));
            }
            if (actual[i] == null)
            {
                throw new Exception("Expected " + data[i] + " got null");
            }
            if (!data[i].equals(actual[i]))
            {
                throw new Exception("Expected " + data[i] + " got " + actual[i]);
            }
        }
    }


    private void close() throws Exception
    {
        session.close();
        connection.close();
    }

    private synchronized void waitUntilReceived(int count) throws InterruptedException
    {
        waiting = true;
        while (received < count)
        {
            wait();
        }
        waiting = false;
    }

    public void onMessage(Message message)
    {
        received++;
        try
        {
            if (message instanceof ObjectMessage)
            {
                items.add(((ObjectMessage) message).getObject());
            }
            else
            {
                System.out.println("ERROR: Got " + message.getClass().getName() + " not ObjectMessage");
                items.add(message);
            }
        }
        catch (JMSException e)
        {
            e.printStackTrace();
            items.add(e);
        }

        if (waiting)
        {
            synchronized(this)
            {
                notify();
            }
        }
    }


    @Test
    public void doJUnitTest()
    {
        try
        {
            new ObjectMessageTest("vm://:1").test();
        }
        catch (Exception e)
        {
            Assert.fail("This Test should succeed but failed due to: " + e);
        }
    }

    public static void main(String[] argv) throws Exception
    {
        String broker = argv.length > 0 ? argv[0] : "vm://:1";
        if ("-help".equals(broker))
        {
            System.out.println("Usage: <broker>");
        }
        new ObjectMessageTest(broker).test();
    }

    private static class A implements Serializable
    {
        private String sValue;
        private int iValue;

        A(int i, String s)
        {
            sValue = s;
            iValue = i;
        }

        public int hashCode()
        {
            return iValue;
        }

        public boolean equals(Object o)
        {
            return o instanceof A && equals((A) o);
        }

        protected boolean equals(A a)
        {
            return areEqual(a.sValue, sValue) && a.iValue == iValue;
        }
    }

    private static class B extends A
    {
        private long time;

        B(int i, String s)
        {
            super(i, s);
            time = System.currentTimeMillis();
        }

        protected boolean equals(A a)
        {
            return super.equals(a) && a instanceof B && time == ((B) a).time;
        }
    }

    private static class C extends HashMap implements Serializable
    {
    }

    private static boolean areEqual(Object a, Object b)
    {
        return a == null ? b == null : a.equals(b);
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

}
