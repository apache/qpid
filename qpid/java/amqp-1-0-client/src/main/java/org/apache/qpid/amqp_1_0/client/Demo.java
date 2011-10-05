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

package org.apache.qpid.amqp_1_0.client;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Demo extends Util
{
    private static final String USAGE_STRING = "demo [options] <vendor> [<content> ...]\n\nOptions:";
    private static final String OPCODE = "opcode";
    private static final String ACTION = "action";
    private static final String MESSAGE_ID = "message-id";
    private static final String VENDOR = "vendor";
    private static final String LOG = "log";
    private static final String RECEIVED = "received";
    private static final String TEST = "test";
    private static final String APACHE = "apache";
    private static final String SENT = "sent";
    private static final String LINK_REF = "link-ref";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String SASL_USER = "sasl-user";
    private static final String SASL_PASSWORD = "sasl-password";
    private static final String ROLE = "role";
    private static final String ADDRESS = "address";
    private static final String SENDER = "sender";
    private static final String SEND_MESSAGE = "send-message";
    private static final String ANNOUNCE = "announce";
    private static final String MESSAGE_VENDOR = "message-vendor";
    private static final String CREATE_LINK = "create-link";

    public static void main(String[] args)
    {
        new Demo(args).run();
    }

    public Demo(String[] args)
    {
        super(args);
    }

    @Override
    protected boolean hasLinkDurableOption()
    {
        return false;
    }

    @Override
    protected boolean hasLinkNameOption()
    {
        return false;
    }

    @Override
    protected boolean hasResponseQueueOption()
    {
        return false;
    }

    @Override
    protected boolean hasSizeOption()
    {
        return false;
    }

    @Override
    protected boolean hasBlockOption()
    {
        return false;
    }

    @Override
    protected boolean hasStdInOption()
    {
        return false;
    }

    @Override
    protected boolean hasTxnOption()
    {
        return false;
    }

    @Override
    protected boolean hasModeOption()
    {
        return true;
    }

    @Override
    protected boolean hasCountOption()
    {
        return false;
    }

    @Override
    protected boolean hasWindowSizeOption()
    {
        return false;
    }

    public void run()
    {

        try
        {

            final String vendor = getArgs()[0];
            final String queue = "control";

            String message = "";

            Connection conn = newConnection();
            Session session = conn.createSession();


            Receiver responseReceiver;

            responseReceiver = session.createTemporaryQueueReceiver();




            responseReceiver.setCredit(UnsignedInteger.valueOf(getWindowSize()), true);



            Sender s = session.createSender(queue, getWindowSize(), getMode());


            Properties properties = new Properties();
            properties.setMessageId(java.util.UUID.randomUUID());
            properties.setReplyTo(responseReceiver.getAddress());

            HashMap appPropMap = new HashMap();
            ApplicationProperties appProperties = new ApplicationProperties(appPropMap);

            appPropMap.put(OPCODE, ANNOUNCE);
            appPropMap.put(VENDOR, vendor);
            appPropMap.put(ADDRESS,responseReceiver.getAddress());

            AmqpValue amqpValue = new AmqpValue(message);
            Section[] sections = { properties, appProperties, amqpValue};
            final Message message1 = new Message(Arrays.asList(sections));

            s.send(message1);

            Map<Object, Sender> sendingLinks = new HashMap<Object, Sender>();
            Map<Object, Receiver> receivingLinks = new HashMap<Object, Receiver>();


            boolean done = false;

            while(!done)
            {
                boolean wait = true;
                Message m = responseReceiver.receive(false);
                if(m != null)
                {
                    List<Section> payload = m.getPayload();
                    wait = false;
                    ApplicationProperties props = m.getApplicationProperties();
                    Map map = props.getValue();
                    String op = (String) map.get(OPCODE);
                    if("reset".equals(op))
                    {
                        for(Sender sender : sendingLinks.values())
                        {
                            try
                            {
                                sender.close();
                                Session session1 = sender.getSession();
                                session1.close();
                                session1.getConnection().close();
                            }
                            catch(Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                        for(Receiver receiver : receivingLinks.values())
                        {
                            try
                            {
                                receiver.close();
                                receiver.getSession().close();
                                receiver.getSession().getConnection().close();
                            }
                            catch(Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                        sendingLinks.clear();
                        receivingLinks.clear();
                    }
                    else if(CREATE_LINK.equals(op))
                    {
                        Object linkRef = map.get(LINK_REF);
                        String host = (String) map.get(HOST);
                        Object o = map.get(PORT);
                        int port = Integer.parseInt(String.valueOf(o));
                        String user = (String) map.get(SASL_USER);
                        String password = (String) map.get(SASL_PASSWORD);
                        String role = (String) map.get(ROLE);
                        String address = (String) map.get(ADDRESS);
                        System.err.println("Host: " + host + "\tPort: " + port + "\t user: " + user +"\t password: " + password);
                        try{


                            Connection conn2 = new Connection(host, port, user, password,
                                                              "development.mycompany.mysystem.stormmq.com");
                            Session session2 = conn2.createSession();
                            if(sendingLinks.containsKey(linkRef))
                            {
                                try
                                {
                                    sendingLinks.remove(linkRef).close();
                                }
                                catch (Exception e)
                                {

                                }
                            }
                            if(receivingLinks.containsKey(linkRef))
                            {
                                try
                                {
                                    receivingLinks.remove(linkRef).close();
                                }
                                catch (Exception e)
                                {

                                }
                            }
                            if(SENDER.equals(role))
                            {

                                System.err.println("%%% Creating sender (" + linkRef + ")");
                                Sender sender = session2.createSender(address);
                                sendingLinks.put(linkRef, sender);
                            }
                            else
                            {

                                System.err.println("%%% Creating receiver (" + linkRef + ")");
                                Receiver receiver2 = session2.createReceiver(address);
                                receiver2.setCredit(UnsignedInteger.valueOf(getWindowSize()), true);

                                receivingLinks.put(linkRef, receiver2);
                            }
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                    else if(SEND_MESSAGE.equals(op))
                    {
                        Sender sender = sendingLinks.get(map.get(LINK_REF));
                        Properties m2props = new Properties();
                        Object messageId = map.get(MESSAGE_ID);
                        m2props.setMessageId(messageId);
                        Map m2propmap = new HashMap();
                        m2propmap.put(OPCODE, TEST);
                        m2propmap.put(VENDOR, vendor);
                        ApplicationProperties m2appProps = new ApplicationProperties(m2propmap);
                        Message m2 = new Message(Arrays.asList(m2props, m2appProps, new AmqpValue("AMQP-"+messageId)));
                        sender.send(m2);

                        Map m3propmap = new HashMap();
                        m3propmap.put(OPCODE, LOG);
                        m3propmap.put(ACTION, SENT);
                        m3propmap.put(MESSAGE_ID, messageId);
                        m3propmap.put(VENDOR, vendor);
                        m3propmap.put(MESSAGE_VENDOR, vendor);


                        Message m3 = new Message(Arrays.asList(new ApplicationProperties(m3propmap),
                                                               new AmqpValue("AMQP-"+messageId)));
                        s.send(m3);

                    }

                    responseReceiver.acknowledge(m);
                }
                else
                {
                    for(Map.Entry<Object, Receiver> entry : receivingLinks.entrySet())
                    {
                        m = entry.getValue().receive(false);
                        if(m != null)
                        {
                            wait = false;

                            System.err.println("%%% Received message from " + entry.getKey());

                            Properties mp = m.getProperties();
                            ApplicationProperties ap = m.getApplicationProperties();

                            Map m3propmap = new HashMap();
                            m3propmap.put(OPCODE, LOG);
                            m3propmap.put(ACTION, RECEIVED);
                            m3propmap.put(MESSAGE_ID, mp.getMessageId());
                            m3propmap.put(VENDOR, vendor);
                            m3propmap.put(MESSAGE_VENDOR, ap.getValue().get(VENDOR));

                            Message m3 = new Message(Arrays.asList(new ApplicationProperties(m3propmap),
                                                                   new AmqpValue("AMQP-"+mp.getMessageId())));
                            s.send(m3);

                            entry.getValue().acknowledge(m);
                        }

                    }
                }

                if(wait)
                {
                    try
                    {
                        Thread.sleep(500l);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();  //TODO.
                    }
                }

            }









            s.close();
            session.close();
            conn.close();

        }
        catch (Connection.ConnectionException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (Sender.SenderClosingException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (Sender.SenderCreationException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (AmqpErrorException e)
        {
            e.printStackTrace();  //TODO.
        }

    }

    protected boolean hasSingleLinkPerConnectionMode()
    {
        return false;
    }

    protected void printUsage(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(USAGE_STRING, options );
    }

}
