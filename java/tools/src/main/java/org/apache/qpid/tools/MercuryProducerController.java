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
package org.apache.qpid.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.thread.Threading;

/**
 * PerfProducer sends an x no of messages in warmup mode and wait for a confirmation
 * from the consumer that it has successfully consumed them and ready to start the
 * test. It will start sending y no of messages and each message will contain a time
 * stamp. This will be used at the receiving end to measure the latency.
 *
 * This is done with the assumption that both consumer and producer are running on
 * the same machine or different machines which have time synced using a time server.
 *
 * This test also calculates the producer rate as follows.
 * rate = msg_count/(time_before_sending_msgs - time_after_sending_msgs)
 *
 * All throughput rates are given as msg/sec so the rates are multiplied by 1000.
 *
 * Rajith - Producer rate is not an accurate perf metric IMO.
 * It is heavily inlfuenced by any in memory buffering.
 * System throughput and latencies calculated by the PerfConsumer are more realistic
 * numbers.
 *
 * Answer by rajith : I agree about in memory buffering affecting rates. But Based on test runs
 * I have done so far, it seems quite useful to compute the producer rate as it gives an
 * indication of how the system behaves. For ex if there is a gap between producer and consumer rates
 * you could clearly see the higher latencies and when producer and consumer rates are very close,
 * latency is good.
 *
 */
public class PerfProducer extends PerfBase
{
    private static long SEC = 60000;

    MessageProducer producer;
    Message msg;
    Object payload;
    List<Object> payloads;
    boolean cacheMsg = false;
    boolean randomMsgSize = false;
    boolean durable = false;
    Random random;
    int msgSizeRange = 1024;
    boolean rateLimitProducer = false;
    double rateFactor = 0.4;
    double rate = 0.0;

    public PerfProducer(String prefix)
    {
        super(prefix);
        System.out.println("Producer ID : " + id);
    }

    public void setUp() throws Exception
    {
        super.setUp();
        durable = params.isDurable();
        rateLimitProducer = params.getRate() > 0 ? true : false;
        if (rateLimitProducer)
        {
            System.out.println("The test will attempt to limit the producer to " + params.getRate() + " msg/sec");
        }

        // if message caching is enabled we pre create the message
        // else we pre create the payload
        if (params.isCacheMessage())
        {
            cacheMsg = true;
            msg = createMessage(createPayload(params.getMsgSize()));
            msg.setJMSDeliveryMode(durable?
                                   DeliveryMode.PERSISTENT :
                                   DeliveryMode.NON_PERSISTENT
                                  );
        }
        else if (params.isRandomMsgSize())
        {
            random = new Random(20080921);
            randomMsgSize = true;
            msgSizeRange = params.getMsgSize();
            payloads = new ArrayList<Object>(msgSizeRange);

            for (int i=0; i < msgSizeRange; i++)
            {
                payloads.add(createPayload(i));
            }
        }
        else
        {
            payload = createPayload(params.getMsgSize());
        }

        producer = session.createProducer(dest);
        System.out.println("Producer: " + id + " Sending messages to: " + ((AMQDestination)dest).getQueueName());
        producer.setDisableMessageID(params.isDisableMessageID());
        producer.setDisableMessageTimestamp(params.isDisableTimestamp());

        MapMessage m = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.REGISTER_PRODUCER.ordinal());
        sendMessageToController(m);
    }

    Object createPayload(int size)
    {
        if (msgType == MessageType.TEXT)
        {
           return MessageFactory.createMessagePayload(size);
        }
        else
        {
            return MessageFactory.createMessagePayload(size).getBytes();
        }
    }

    Message createMessage(Object payload) throws Exception
    {
        if (msgType == MessageType.TEXT)
        {
            return session.createTextMessage((String)payload);
        }
        else
        {
            BytesMessage m = session.createBytesMessage();
            m.writeBytes((byte[])payload);
            return m;
        }
    }

    protected Message getNextMessage() throws Exception
    {
        if (cacheMsg)
        {
            return msg;
        }
        else
        {
            Message m;

            if (!randomMsgSize)
            {
                m = createMessage(payload);
            }
            else
            {
                m = createMessage(payloads.get(random.nextInt(msgSizeRange)));
            }
            m.setJMSDeliveryMode(durable?
                    DeliveryMode.PERSISTENT :
                    DeliveryMode.NON_PERSISTENT
                   );
            return m;
        }
    }

    public void warmup()throws Exception
    {
        receiveFromController(OPCode.PRODUCER_STARTWARMUP);
        System.out.println("Producer: " + id + " Warming up......");

        for (int i=0; i < params.getWarmupCount() -1; i++)
        {
            producer.send(getNextMessage());
        }
        sendEndMessage();

        if (params.isTransacted())
        {
            session.commit();
        }
    }

    public void startTest() throws Exception
    {
        resetCounters();
        receiveFromController(OPCode.PRODUCER_START);
        int count = params.getMsgCount();
        boolean transacted = params.isTransacted();
        int tranSize =  params.getTransactionSize();

        long limit = (long)(params.getRate() * rateFactor); // in msecs
        long timeLimit = (long)(SEC * rateFactor); // in msecs

        long start = Clock.getTime(); // defaults to nano secs
        long interval = start;
        for(int i=0; i < count; i++ )
        {
            Message msg = getNextMessage();
            msg.setLongProperty(TIMESTAMP, Clock.getTime());
            producer.send(msg);
            if ( transacted && ((i+1) % tranSize == 0))
            {
                session.commit();
            }

            if (rateLimitProducer && i%limit == 0)
            {
                long elapsed = (Clock.getTime() - interval)*Clock.convertToMiliSecs(); // in msecs
                if (elapsed < timeLimit)
                {
                    Thread.sleep(elapsed);
                }
                interval = Clock.getTime();

            }
        }
        sendEndMessage();
        if ( transacted)
        {
            session.commit();
        }
        long time = Clock.getTime() - start;
        rate = (double)count*Clock.convertToSecs()/(double)time;
        System.out.println(new StringBuilder("Producer rate: ").
                               append(df.format(rate)).
                               append(" msg/sec").
                               toString());
    }

    public void resetCounters()
    {

    }

    public void sendEndMessage() throws Exception
    {
        Message msg = session.createMessage();
        msg.setBooleanProperty("End", true);
        producer.send(msg);
    }

    public void sendResults() throws Exception
    {
        MapMessage msg = controllerSession.createMapMessage();
        msg.setInt(CODE, OPCode.RECEIVED_PRODUCER_STATS.ordinal());
        msg.setDouble(PROD_RATE, rate);
        sendMessageToController(msg);
    }

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void run()
    {
        try
        {
            setUp();
            warmup();
            boolean nextIteration = true;
            while (nextIteration)
            {
                System.out.println("=========================================================\n");
                System.out.println("Producer: " + id + " starting a new iteration ......\n");
                startTest();
                sendResults();
                nextIteration = continueTest();
            }
            tearDown();
        }
        catch(Exception e)
        {
            handleError(e,"Error when running test");
        }
    }

    public void startControllerIfNeeded()
    {
        if (!params.isExternalController())
        {
            final PerfTestController controller = new PerfTestController();
            Runnable r = new Runnable()
            {
                public void run()
                {
                    controller.run();
                }
            };

            Thread t;
            try
            {
                t = Threading.getThreadFactory().createThread(r);
            }
            catch(Exception e)
            {
                throw new Error("Error creating controller thread",e);
            }
            t.start();
        }
    }


    public static void main(String[] args) throws InterruptedException
    {
        String scriptId = (args.length == 1) ? args[0] : "";
        int conCount = Integer.getInteger("con_count",1);
        final CountDownLatch testCompleted = new CountDownLatch(conCount);
        for (int i=0; i < conCount; i++)
        {
            final PerfProducer prod = new PerfProducer(scriptId + i);
            prod.startControllerIfNeeded();
            Runnable r = new Runnable()
            {
                public void run()
                {
                    prod.run();
                    testCompleted.countDown();
                }
            };

            Thread t;
            try
            {
                t = Threading.getThreadFactory().createThread(r);
            }
            catch(Exception e)
            {
                throw new Error("Error creating producer thread",e);
            }
            t.start();
        }
        testCompleted.await();
        System.out.println("Producers have completed the test......");
    }
}