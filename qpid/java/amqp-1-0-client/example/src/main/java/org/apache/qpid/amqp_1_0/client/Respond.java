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
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.commons.cli.*;

import java.util.*;

public class Respond extends Util
{
    private static final String USAGE_STRING = "respond [options] <address>\n\nOptions:";
    private Connection _conn;
    private Session _session;
    private Receiver _receiver;
    private Transaction _txn;
    private Map<String,Sender> _senders;
    private UnsignedLong _responseMsgId = UnsignedLong.ZERO;
    private Connection _conn2;
    private Session _session2;

    public Respond(final String[] args)
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
        return true;
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
    protected boolean hasSingleLinkPerConnectionMode()
    {
        return true;
    }


    @Override
    protected boolean hasWindowSizeOption()
    {
        return true;
    }

    public static void main(String[] args)
    {
        new Respond(args).run();
    }

    public void run()
    {
        try
        {

            _senders = new HashMap<String, Sender>();

            final String queue = getArgs()[0];

            String message = "";

            _conn = newConnection();



            if(isUseMultipleConnections())
            {
                _conn2 = newConnection();
                _session2 = _conn2.createSession();
            }


            _session = _conn.createSession();


            _receiver = _session.createReceiver(queue, getMode());
            _txn = null;

            int credit = 0;
            int receivedCount  = 0;
            _responseMsgId = UnsignedLong.ZERO;

            Random random = null;
            int batch = 0;
            List<Message> txnMessages = null;
            if(useTran())
            {
                if(getRollbackRatio() != 0)
                {
                    random = new Random();
                }
                batch = getBatchSize();
                _txn = _session.createSessionLocalTransaction();
                txnMessages = new ArrayList<Message>(batch);
            }


            for(int i = 0; receivedCount < getCount(); i++)
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

                    _receiver.setCredit(UnsignedInteger.valueOf(credit), false);

                    if(!isBlock())
                        _receiver.drain();
                }

                Message m = isBlock() ? (receivedCount == 0 ? _receiver.receive() : _receiver.receive(10000L)) : _receiver.receive(1000L);
                credit--;
                if(m==null)
                {
                    if(useTran() && batch != getBatchSize())
                    {
                        _txn.commit();
                    }
                    break;
                }

                System.out.println("Received Message: " + m.getPayload());

                respond(m);



                if(useTran())
                {

                    txnMessages.add(m);

                    if(--batch == 0)
                    {

                        if(getRollbackRatio() == 0 || random.nextDouble() >= getRollbackRatio())
                        {
                            _txn.commit();
                            txnMessages.clear();
                            receivedCount += getBatchSize();
                        }
                        else
                        {
                            System.out.println("Random Rollback");
                            _txn.rollback();
                            double result;
                            do
                            {
                                _txn = _session.createSessionLocalTransaction();

                                for(Message msg : txnMessages)
                                {
                                    respond(msg);
                                }

                                result = random.nextDouble();
                                if(result<getRollbackRatio())
                                {
                                    _txn.rollback();
                                }
                                else
                                {
                                    _txn.commit();
                                    txnMessages.clear();
                                    receivedCount += getBatchSize();
                                }
                            }
                            while(result < getRollbackRatio());
                        }
                        _txn = _session.createSessionLocalTransaction();

                        batch = getBatchSize();
                    }
                }
                else
                {
                    receivedCount++;
                }

            }


            for(Sender s : _senders.values())
            {
                s.close();
            }

            _receiver.close();
            _session.close();
            _conn.close();
            System.out.println("Received: " + receivedCount);
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

    private void respond(Message m) throws Sender.SenderCreationException
    {
        List<Section> sections = m.getPayload();
        String replyTo = null;
        Object correlationId = null;
        for(Section section : sections)
        {
            if(section instanceof Properties)
            {
                replyTo = getResponseQueue() == null ? ((Properties)section).getReplyTo() : getResponseQueue();
                correlationId = ((Properties) section).getMessageId();
                break;
            }
        }

        if(replyTo != null)
        {
            Sender s = _senders.get(replyTo);
            if(s == null)
            {
                s = (isUseMultipleConnections() ? _session2 : _session).createSender(replyTo,getWindowSize());
                _senders.put(replyTo, s);
            }

            List<Section> replySections = new ArrayList<Section>(sections);

            ListIterator<Section> sectionIterator = replySections.listIterator();

            while(sectionIterator.hasNext())
            {
                Section section = sectionIterator.next();
                if(section instanceof Properties)
                {
                    Properties newProps = new Properties();
                    newProps.setTo(replyTo);
                    newProps.setCorrelationId(correlationId);
                    newProps.setMessageId(_responseMsgId);
                    _responseMsgId = _responseMsgId.add(UnsignedLong.ONE);
                    sectionIterator.set(newProps);
                }
            }

            Message replyMessage = new Message(replySections);
            System.out.println("Sent Message: " + replySections);
            s.send(replyMessage, _txn);

        }
        _receiver.acknowledge(m.getDeliveryTag(), _txn);
    }

    protected  void printUsage(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(USAGE_STRING, options );
    }

}
