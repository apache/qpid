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

package org.apache.qpid.amqp_1_0.jms.example;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;


public class Hello
{

    public Hello()
    {
    }

    public static void main(String[] args)
    {
        try
        {
            Class.forName("org.apache.qpid.amqp_1_0.jms.jndi.PropertiesFileInitialContextFactory");

            Hashtable env = new Hashtable();
            env.put("java.naming.provider.url", "hello.properties");
            env.put("java.naming.factory.initial", "org.apache.qpid.amqp_1_0.jms.jndi.PropertiesFileInitialContextFactory");

            Context context = new InitialContext(env);

            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("localhost");
            Connection connection = connectionFactory.createConnection();

            Session producersession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = (Queue) context.lookup("queue");


            Session consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = consumerSession.createConsumer(queue, "hello='true'");

            messageConsumer.setMessageListener(new MessageListener()
            {
                public void onMessage(final Message message)
                {
                    try
                    {

                        if(message instanceof TextMessage)
                        {
                            System.out.println("Received text Message:");
                            System.out.println("======================");
                            System.out.println(((TextMessage) message).getText());
                        }
                        else if(message instanceof MapMessage)
                        {
                            System.out.println("Received Map Message:");
                            System.out.println("=====================");


                            MapMessage mapmessage = (MapMessage) message;

                            Enumeration names = mapmessage.getMapNames();

                            while(names.hasMoreElements())
                            {
                                String name = (String) names.nextElement();
                                System.out.println(name + " -> " + mapmessage.getObject(name));
                            }

                        }
                        else if(message instanceof BytesMessage)
                        {
                            System.out.println("Received Bytes Message:");
                            System.out.println("=======================");
                            System.out.println(((BytesMessage) message).readUTF());
                        }
                        else if(message instanceof StreamMessage)
                        {
                            System.out.println("Received Stream Message:");
                            System.out.println("========================");
                            StreamMessage streamMessage = (StreamMessage)message;
                            Object o = streamMessage.readObject();
                            System.out.println(o.getClass().getName() + ": " + o);
                            o = streamMessage.readObject();
                            System.out.println(o.getClass().getName() + ": " + o);
                            o = streamMessage.readObject();
                            System.out.println(o.getClass().getName() + ": " + o);

                        }
                        else if(message instanceof ObjectMessage)
                        {
                            System.out.println("Received Object Message:");
                            System.out.println("========================");
                            ObjectMessage objectMessage = (ObjectMessage)message;
                            Object o = objectMessage.getObject();
                            System.out.println(o.getClass().getName() + ": " + o);
                        }
                        else
                        {
                            System.out.println("Received Message " + message.getClass().getName());
                        }
                    }
                    catch (JMSException e)
                    {
                        e.printStackTrace();  //TODO
                    }

                }
            });

            connection.start();


            MessageProducer messageProducer = producersession.createProducer(queue);
            TextMessage message = producersession.createTextMessage("Hello world!");
            message.setJMSType("Hello");
            message.setStringProperty("hello","true");
            messageProducer.send(message);
           /*
            MapMessage mapmessage = producersession.createMapMessage();
            mapmessage.setBoolean("mybool", true);
            mapmessage.setString("mystring", "hello");
            mapmessage.setLong("mylong", -25L);


            messageProducer.send(mapmessage);

            BytesMessage bytesMessage = producersession.createBytesMessage();
            bytesMessage.writeUTF("This is a bytes message");

            messageProducer.send(bytesMessage);

            ObjectMessage objectMessage = producersession.createObjectMessage();
            objectMessage.setObject(new Double("3.14159265358979323846264338327950288"));

            messageProducer.send(objectMessage);

/*          StreamMessage streamMessage = producersession.createStreamMessage();
            streamMessage.writeBoolean(true);
            streamMessage.writeLong(18031974L);
            streamMessage.writeString("this is a stream Message");
            streamMessage.writeChar('£');
            messageProducer.send(streamMessage);
*/
            Thread.sleep(50000L);

            connection.close();
            context.close();
        }
        catch (Exception exp)
        {
            exp.printStackTrace();
        }
    }
}
