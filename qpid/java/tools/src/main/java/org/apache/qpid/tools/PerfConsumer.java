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

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.qpid.thread.Threading;

/**
 * PerfConsumer will receive x no of messages in warmup mode.
 * Once it receives the Start message it will then signal the PerfProducer.
 * It will start recording stats from the first message it receives after
 * the warmup mode is done.
 *
 * The following calculations are done.
 * The important numbers to look at is
 * a) Avg Latency
 * b) System throughput.
 *
 * Latency.
 * =========
 * Currently this test is written with the assumption that either
 * a) The Perf Producer and Consumer are on the same machine
 * b) They are on separate machines that have their time synced via a Time Server
 *
 * In order to calculate latency the producer inserts a timestamp
 * hen the message is sent. The consumer will note the current time the message is
 * received and will calculate the latency as follows
 * latency = rcvdTime - msg.getJMSTimestamp()
 *
 * Through out the test it will keep track of the max and min latency to show the
 * variance in latencies.
 *
 * Avg latency is measured by adding all latencies and dividing by the total msgs.
 * You can also compute this by (rcvdTime - testStartTime)/rcvdMsgCount
 *
 * Throughput
 * ===========
 * System throughput is calculated as follows
 * rcvdMsgCount/(rcvdTime - testStartTime)
 *
 * Consumer rate is calculated as
 * rcvdMsgCount/(rcvdTime - startTime)
 *
 * Note that the testStartTime referes to when the producer sent the first message
 * and startTime is when the consumer first received a message.
 *
 * rcvdTime keeps track of when the last message is received.
 *
 * All throughput rates are given as msg/sec so the rates are multiplied by 1000.
 *
 */

public class PerfConsumer extends PerfBase implements MessageListener
{
    MessageConsumer consumer;
    long maxLatency = 0;
    long minLatency = Long.MAX_VALUE;
    long totalLatency = 0;  // to calculate avg latency.
    int rcvdMsgCount = 0;
    long testStartTime = 0; // to measure system throughput
    long startTime = 0;     // to measure consumer throughput
    long rcvdTime = 0;
    boolean transacted = false;
    int transSize = 0;

    boolean printStdDev = false;
    List<Long> sample;

    final Object lock = new Object();

    public PerfConsumer()
    {
        super();
        System.out.println("Consumer ID : " + id);
    }

    public void setUp() throws Exception
    {
        super.setUp();
        consumer = session.createConsumer(dest);

        // Storing the following two for efficiency
        transacted = params.isTransacted();
        transSize = params.getTransactionSize();
        printStdDev = params.isPrintStdDev();
        if (printStdDev)
        {
            sample = new ArrayList<Long>(params.getMsgCount());
        }

        MapMessage m = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.REGISTER_CONSUMER.ordinal());
        m.setString(REPLY_ADDR,myControlQueueAddr);
        sendMessageToController(m);
    }

    public void warmup()throws Exception
    {
        receiveFromController(OPCode.CONSUMER_STARTWARMUP);
        boolean start = false;
        Message msg = consumer.receive();
        // This is to ensure we drain the queue before we start the actual test.
        while ( msg != null)
        {
            if (msg.getBooleanProperty("End") == true)
            {
                // It's more realistic for the consumer to signal this.
                MapMessage m = controllerSession.createMapMessage();
                m.setInt(CODE, OPCode.PRODUCER_READY.ordinal());
                sendMessageToController(m);
            }
            msg = consumer.receive(1000);
        }

        if (params.isTransacted())
        {
            session.commit();
        }

        MapMessage m = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.CONSUMER_READY.ordinal());
        sendMessageToController(m);
    }

    public void startTest() throws Exception
    {
        System.out.println("Consumer Starting test......");
        consumer.setMessageListener(this);
    }

    public void sendResults() throws Exception
    {
        receiveFromController(OPCode.CONSUMER_STOP);

        double avgLatency = (double)totalLatency/(double)rcvdMsgCount;
        double consRate   = (double)rcvdMsgCount*Clock.convertToSecs()/(double)(rcvdTime - startTime);
        double stdDev = 0.0;
        if (printStdDev)
        {
            stdDev = calculateStdDev(avgLatency);
        }
        MapMessage m  = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.RECEIVED_CONSUMER_STATS.ordinal());
        m.setDouble(AVG_LATENCY, avgLatency/Clock.convertToMiliSecs());
        m.setDouble(MIN_LATENCY,minLatency/Clock.convertToMiliSecs());
        m.setDouble(MAX_LATENCY,maxLatency/Clock.convertToMiliSecs());
        m.setDouble(STD_DEV, stdDev/Clock.convertToMiliSecs());
        m.setDouble(CONS_RATE, consRate);
        m.setLong(MSG_COUNT, rcvdMsgCount);
        sendMessageToController(m);

        System.out.println(new StringBuilder("Total Msgs Received : ").append(rcvdMsgCount).toString());
        System.out.println(new StringBuilder("Consumer rate       : ").
                           append(df.format(consRate)).
                           append(" msg/sec").toString());
        System.out.println(new StringBuilder("Avg Latency         : ").
                           append(df.format(avgLatency/Clock.convertToMiliSecs())).
                           append(" ms").toString());
        System.out.println(new StringBuilder("Min Latency         : ").
                           append(df.format(minLatency/Clock.convertToMiliSecs())).
                           append(" ms").toString());
        System.out.println(new StringBuilder("Max Latency         : ").
                           append(df.format(maxLatency/Clock.convertToMiliSecs())).
                           append(" ms").toString());
        if (printStdDev)
        {
            System.out.println(new StringBuilder("Std Dev             : ").
                               append(stdDev/Clock.convertToMiliSecs()).toString());
        }
        System.out.println("Consumer has completed the test......\n");
    }

    public double calculateStdDev(double mean)
    {
        double v = 0;
        for (double latency: sample)
        {
            v = v + Math.pow((latency-mean), 2);
        }
        v = v/sample.size();
        return Math.round(Math.sqrt(v));
    }

    public void onMessage(Message msg)
    {
        try
        {
            // To figure out the decoding overhead of text
            if (msgType == MessageType.TEXT)
            {
                ((TextMessage)msg).getText();
            }

            if (msg.getBooleanProperty("End"))
            {
                MapMessage m = controllerSession.createMapMessage();
                m.setInt(CODE, OPCode.RECEIVED_END_MSG.ordinal());
                sendMessageToController(m);
            }
            else
            {
                rcvdTime = Clock.getTime();
                rcvdMsgCount ++;

                if (rcvdMsgCount == 1)
                {
                    startTime = rcvdTime;
                }

                if (transacted && (rcvdMsgCount % transSize == 0))
                {
                    session.commit();
                }

                long latency = rcvdTime - msg.getLongProperty(TIMESTAMP);
                maxLatency = Math.max(maxLatency, latency);
                minLatency = Math.min(minLatency, latency);
                totalLatency = totalLatency + latency;
                if (printStdDev)
                {
                    sample.add(latency);
                }
            }

        }
        catch(Exception e)
        {
            handleError(e,"Error when receiving messages");
        }

    }

    public void run()
    {
        try
        {
            setUp();
            warmup();
            startTest();
            sendResults();
            tearDown();
        }
        catch(Exception e)
        {
            handleError(e,"Error when running test");
        }
    }

    public static void main(String[] args)
    {
        final PerfConsumer cons = new PerfConsumer();
        Runnable r = new Runnable()
        {
            public void run()
            {
                cons.run();
            }
        };

        Thread t;
        try
        {
            t = Threading.getThreadFactory().createThread(r);
        }
        catch(Exception e)
        {
            throw new Error("Error creating consumer thread",e);
        }
        t.start();
    }
}