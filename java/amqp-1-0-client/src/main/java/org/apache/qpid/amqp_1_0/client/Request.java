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

import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.commons.cli.*;

import java.util.Arrays;

public class Request extends Util
{
    private static final String USAGE_STRING = "request [options] <address> [<content> ...]\n\nOptions:";

    public static void main(String[] args)
    {
        new Request(args).run();
    }

    public Request(String[] args)
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
        return true;
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
        return true;
    }

    @Override
    protected boolean hasModeOption()
    {
        return true;
    }

    @Override
    protected boolean hasCountOption()
    {
        return true;
    }

    @Override
    protected boolean hasWindowSizeOption()
    {
        return true;
    }

    public void run()
    {

        try
        {


            final String queue = getArgs()[0];

            String message = "";

            Connection conn = newConnection();
            Session session = conn.createSession();

            Connection conn2;
            Session session2;
            Receiver responseReceiver;

            if(isUseMultipleConnections())
            {
                conn2 = newConnection();
                session2 = conn2.createSession();
                responseReceiver = session2.createTemporaryQueueReceiver();
            }
            else
            {
                conn2 = null;
                session2 = null;
                responseReceiver = session.createTemporaryQueueReceiver();
            }




            responseReceiver.setCredit(UnsignedInteger.valueOf(getWindowSize()), true);



            Sender s = session.createSender(queue, getWindowSize(), getMode());

            Transaction txn = null;

            if(useTran())
            {
                txn = session.createSessionLocalTransaction();
            }

            int received = 0;

            if(getArgs().length >= 2)
            {
                message = getArgs()[1];
                if(message.length() < getMessageSize())
                {
                    StringBuilder builder = new StringBuilder(getMessageSize());
                    builder.append(message);
                    for(int x = message.length(); x < getMessageSize(); x++)
                    {
                        builder.append('.');
                    }
                    message = builder.toString();
                }

                for(int i = 0; i < getCount(); i++)
                {
                    Properties properties = new Properties();
                    properties.setMessageId(UnsignedLong.valueOf(i));
                    properties.setReplyTo(responseReceiver.getAddress());

                    AmqpValue amqpValue = new AmqpValue(message);
                    Section[] sections = { new Header() , properties, amqpValue};
                    final Message message1 = new Message(Arrays.asList(sections));

                    s.send(message1, txn);

                    Message responseMessage = responseReceiver.receive(false);
                    if(responseMessage != null)
                    {
                        responseReceiver.acknowledge(responseMessage.getDeliveryTag(),txn);
                        received++;
                    }
                }
            }

            if(txn != null)
            {
                txn.commit();
            }


            while(received < getCount())
            {
                Message responseMessage = responseReceiver.receive();
                responseReceiver.acknowledge(responseMessage.getDeliveryTag());
                received++;
            }




            s.close();
            session.close();
            conn.close();

            if(session2 != null)
            {
                session2.close();
                conn2.close();
            }
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
        return true;
    }

    protected void printUsage(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(USAGE_STRING, options );
    }

}
