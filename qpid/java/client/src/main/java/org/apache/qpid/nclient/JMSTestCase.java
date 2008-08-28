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
package org.apache.qpid.nclient;

import java.util.Enumeration;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueBrowser;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.framing.AMQShortString;

public class JMSTestCase
{

    public static void main(String[] args)
    {

        try
        {
            javax.jms.Connection con = new AMQConnection("qpid:password=pass;username=name@tcp:localhost:5672");
            con.start();

            javax.jms.Session ssn = con.createSession(false, 1);

            javax.jms.Destination dest = new AMQQueue(new AMQShortString("direct"),"test");
            javax.jms.MessageProducer prod = ssn.createProducer(dest);
            QueueBrowser browser = ssn.createBrowser((Queue)dest, "Test = 'test'");

            javax.jms.TextMessage msg = ssn.createTextMessage();
            msg.setStringProperty("TEST", "test");
            msg.setText("Should get this");
            prod.send(msg);

            javax.jms.TextMessage msg2 = ssn.createTextMessage();
            msg2.setStringProperty("TEST", "test2");
            msg2.setText("Shouldn't get this");
            prod.send(msg2);


           Enumeration enu = browser.getEnumeration();
           for (;enu.hasMoreElements();)
           {
               System.out.println(enu.nextElement());
               System.out.println("\n");
           }

           javax.jms.MessageConsumer cons = ssn.createConsumer(dest, "Test = 'test'");
           javax.jms.TextMessage m = null; // (javax.jms.TextMessage)cons.receive();
           cons.setMessageListener(new MessageListener()
            {
                public void onMessage(Message m)
                {
                    javax.jms.TextMessage m2 = (javax.jms.TextMessage)m;
                    try
                    {
                        System.out.println("headers : " + m2.toString());
                        System.out.println("m : " + m2.getText());
                        System.out.println("\n\n");
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }

            });

           con.setExceptionListener(new ExceptionListener()
           {
               public void onException(JMSException e)
               {
                   e.printStackTrace();
               }
           });

           System.out.println("Waiting");
           while (m == null)
           {

           }

           System.out.println("Exiting");

            /*javax.jms.TextMessage msg = ssn.createTextMessage();
            msg.setText("This is a test message");
            msg.setBooleanProperty("targetMessage", false);
            prod.send(msg);

            msg.setBooleanProperty("targetMessage", true);
            prod.send(msg);

            javax.jms.TextMessage m = (javax.jms.TextMessage)cons.receiveNoWait();

            if (m == null)
            {
               System.out.println("message is null");
            }
            else
            {
               System.out.println("message is not null"  + m);
            }*/

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

}
