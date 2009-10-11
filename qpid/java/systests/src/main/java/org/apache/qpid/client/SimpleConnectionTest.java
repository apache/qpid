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
package org.apache.qpid.client;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;

public class SimpleConnectionTest extends TestCase
{
/*    public void testConnection()
    {
        try
        {
            AMQConnection conn = new AMQConnection("127.0.0.1", 5673, "guest", "guest", "test", "/test");


            QueueSession s = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueSender p = s.createSender(new AMQQueue("amq.direct", "queue"));
            for(int i = 0; i < 6000; i++)
            {
                p.send(s.createTextMessage("test("+i+")"));
            }

            QueueReceiver r = s.createReceiver(new AMQQueue("amq.direct", "queue"));
            conn.start();
            Thread.sleep(1000L);
            for(int i = 0; i < 3000; i++)
            {
                Message m = r.receive();
            }
            conn.close();

            conn = new AMQConnection("127.0.0.1", 5673, "guest", "guest", "test", "/test");
            s = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            r = s.createReceiver(new AMQQueue("amq.direct", "queue"));
            conn.start();
            Message m;
            int rcvCnt = 0;
            while((m = r.receive(1000))!= null)
            {
                rcvCnt++;
            }
            System.out.print(rcvCnt);

            Thread.sleep(60000l);

        }
        catch (AMQException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (URLSyntaxException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (JMSException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    public void testConnection2()
    {
        try
        {
            AMQConnection conn = new AMQConnection("127.0.0.1", 5673, "guest", "guest", "test", "/test");
            AMQConnection conn2 = new AMQConnection("127.0.0.1", 5673, "guest", "guest", "test", "/test");

            AMQQueue amqQueue = new AMQQueue("amq.direct", "queue");

            QueueSession s = conn.createQueueSession(true, Session.SESSION_TRANSACTED);
            QueueSender p = s.createSender(amqQueue);

            QueueSession s2 = conn2.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueReceiver r2 = s2.createReceiver(amqQueue);
            r2.setMessageListener(new MessageListener()
            {

                public void onMessage(Message message)
                {
                    try
                    {
                        System.out.println("***************************************************************************");
                        System.out.println("***************************************************************************");
                        System.out.println("**  " +((TextMessage)message).getText());
                        System.out.println("***************************************************************************");
                        System.out.println("***************************************************************************");
                    }
                    catch (JMSException e)
                    {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            });
            conn2.start();

            for(int i = 0; i < 6000; i++)
            {
                p.send(s.createTextMessage("test("+i+")"));
                if(i%10 == 0)
                {   Thread.sleep(5000);
                    s.commit();

                }
            }
        }
        catch (JMSException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (URLSyntaxException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (AMQException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
*/}
