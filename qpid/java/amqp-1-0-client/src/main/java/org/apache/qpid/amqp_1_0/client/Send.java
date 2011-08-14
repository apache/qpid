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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.commons.cli.*;

public class Send extends Util
{
    private static final String USAGE_STRING = "send [options] <address> [<content> ...]\n\nOptions:";
    private static final char[] HEX =  {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};


    public static void main(String[] args) throws Sender.SenderCreationException, Sender.SenderClosingException, Connection.ConnectionException
    {
        new Send(args).run();
    }


    public Send(final String[] args)
    {
        super(args);
    }

    @Override
    protected boolean hasLinkDurableOption()
    {
        return true;
    }

    @Override
    protected boolean hasLinkNameOption()
    {
        return true;
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
        return true;
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

    @Override
    protected boolean hasSubjectOption()
    {
        return true;
    }

    public void run()
    {

        final String queue = getArgs()[0];

        String message = "";

        try
        {
            Connection conn = newConnection();

            Session session = conn.createSession();


            Sender s = session.createSender(queue, getWindowSize(), getMode(), getLinkName());

            Transaction txn = null;

            if(useTran())
            {
                txn = session.createSessionLocalTransaction();
            }

            if(!useStdIn())
            {
                if(getArgs().length <= 2)
                {
                    if(getArgs().length == 2)
                    {
                        message = getArgs()[1];
                    }
                    for(int i = 0; i < getCount(); i++)
                    {

                        Properties properties = new Properties();
                        properties.setMessageId(UnsignedLong.valueOf(i));
                        if(getSubject() != null)
                        {
                            properties.setSubject(getSubject());
                        }
                        Section bodySection;
                        byte[] bytes = (message + "  " + i).getBytes();
                        if(bytes.length < getMessageSize())
                        {
                            byte[] origBytes = bytes;
                            bytes = new byte[getMessageSize()];
                            System.arraycopy(origBytes,0,bytes,0,origBytes.length);
                            for(int x = origBytes.length; x < bytes.length; x++)
                            {
                                bytes[x] = (byte) '.';
                            }
                            bodySection = new Data(new Binary(bytes));
                        }
                        else
                        {
                            bodySection = new AmqpValue(message + " " + i);
                        }

                        Section[] sections = {properties, bodySection};
                        final Message message1 = new Message(Arrays.asList(sections));

                        s.send(message1, txn);
                    }
                }
                else
                {
                    for(int i = 1; i < getArgs().length; i++)
                    {
                        s.send(new Message(getArgs()[i]), txn);
                    }

                }
            }
            else
            {
                LineNumberReader buf = new LineNumberReader(new InputStreamReader(System.in));


                try
                {
                    while((message = buf.readLine()) != null)
                    {
                        s.send(new Message(message), txn);
                    }
                }
                catch (IOException e)
                {
    // TODO
                    e.printStackTrace();
                }
            }

            if(txn != null)
            {
                txn.commit();
            }

            s.close();

            session.close();
            conn.close();
        }
        catch (Sender.SenderClosingException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (Connection.ConnectionException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (Sender.SenderCreationException e)
        {
            e.printStackTrace();  //TODO.
        }


    }

    protected void printUsage(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(USAGE_STRING, options );
    }

}
