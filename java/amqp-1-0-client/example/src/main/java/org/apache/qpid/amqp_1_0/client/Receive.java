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
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.commons.cli.*;
import org.apache.qpid.amqp_1_0.type.messaging.ExactSubjectFilter;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.apache.qpid.amqp_1_0.type.messaging.MatchingSubjectFilter;

import java.util.Collections;

public class Receive extends Util
{
    private static final String USAGE_STRING = "receive [options] <address> \n\nOptions:";
    private static final UnsignedLong UNSIGNED_LONG_ONE = UnsignedLong.valueOf(1L);
    private UnsignedLong _lastCorrelationId;

    public static void main(String[] args)
    {
        new Receive(args).run();
    }


    public Receive(final String[] args)
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
        return false;
    }

    @Override
    protected boolean hasBlockOption()
    {
        return true;
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
    protected boolean hasFilterOption()
    {
        return true;
    }

    protected void run()
    {

        try
        {
            final String queue = getArgs()[0];

            String message = "";

            Connection conn = newConnection();


            Session session = conn.createSession();

            Filter filter = null;
            if(getFilter() != null)
            {
                String[] filterParts  = getFilter().split("=",2);
                if("exact-subject".equals(filterParts[0]))
                {
                    filter = new ExactSubjectFilter(filterParts[1]);
                }
                else if("matching-subject".equals(filterParts[0]))
                {
                    filter = new MatchingSubjectFilter(filterParts[1]);
                }
                else
                {
                    System.err.println("Unknown filter type: " + filterParts[0]);
                }
            }

            Receiver r =
                    filter == null
                        ? session.createReceiver(queue, getMode(), getLinkName(), isDurableLink())
                        : session.createReceiver(queue, getMode(), getLinkName(), isDurableLink(), Collections.singletonMap(Symbol.valueOf("filter"), filter), null);
            Transaction txn = null;

            int credit = 0;
            int receivedCount  = 0;

            if(!useStdIn())
            {
                if(getArgs().length <= 2)
                {

                    Transaction txn2 = null;
                    if(useTran())
                    {
                        txn = session.createSessionLocalTransaction();
                        txn2 = session.createSessionLocalTransaction();
                    }

                    for(int i = 0; i < getCount(); i++)
                    {

                        if(credit == 0)
                        {
                            if(getCount() - i <= getWindowSize())
                            {
                                credit = getCount() - i;

                            }
                            else
                            {
                                credit = getWindowSize();

                            }

                            {
                                r.setCredit(UnsignedInteger.valueOf(credit), false);
                            }
                            if(!isBlock())
                                r.drain();
                        }

                        Message m = isBlock() ? r.receive() : r.receive(1000L);
                        credit--;
                        if(m==null)
                        {
                            break;
                        }



                        r.acknowledge(m.getDeliveryTag(),txn);

                        receivedCount++;

                        System.out.println("Received Message : " + m.getPayload());
                    }

                    if(useTran())
                    {
                        txn.commit();
                    }
                }
                else
                {
                    // TODO
                }
            }
            else
            {
                // TODO
            }
            r.close();
            session.close();
            conn.close();
            System.out.println("Total Messages Received: " + receivedCount);
        }
        catch (Connection.ConnectionException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (AmqpErrorException e)
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
