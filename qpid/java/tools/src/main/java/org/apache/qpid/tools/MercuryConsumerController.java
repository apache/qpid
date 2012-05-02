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

import java.util.concurrent.CountDownLatch;

import javax.jms.MapMessage;

import org.apache.qpid.thread.Threading;
import org.apache.qpid.tools.report.MercuryReporter;
import org.apache.qpid.tools.report.MercuryReporter.MercuryThroughputAndLatency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * when the message is sent. The consumer will note the current time the message is
 * received and will calculate the latency as follows
 * latency = rcvdTime - msg.getJMSTimestamp()
 *
 * Through out the test it will keep track of the max and min latency to show the
 * variance in latencies.
 *
 * Avg latency is measured by adding all latencies and dividing by the total msgs.
 *
 * Throughput
 * ===========
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

public class MercuryConsumerController extends MercuryBase
{
    private static final Logger _logger = LoggerFactory.getLogger(MercuryConsumerController.class);
    MercuryReporter reporter;
    TestConfiguration config;
    QpidReceive receiver;

    public MercuryConsumerController(TestConfiguration config, MercuryReporter reporter, String prefix)
    {
        super(config,prefix);
        this.reporter = reporter;
        if (_logger.isInfoEnabled())
        {
            _logger.info("Consumer ID : " + id);
        }
    }

    public void setUp() throws Exception
    {
        super.setUp();
        receiver = new QpidReceive(reporter,config, con,dest);
        receiver.setUp();
        MapMessage m = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.REGISTER_CONSUMER.ordinal());
        sendMessageToController(m);
    }

    public void warmup()throws Exception
    {
        receiveFromController(OPCode.CONSUMER_STARTWARMUP);
        receiver.waitforCompletion(config.getWarmupCount());

        // It's more realistic for the consumer to signal this.
        MapMessage m1 = controllerSession.createMapMessage();
        m1.setInt(CODE, OPCode.PRODUCER_READY.ordinal());
        sendMessageToController(m1);

        MapMessage m2 = controllerSession.createMapMessage();
        m2.setInt(CODE, OPCode.CONSUMER_READY.ordinal());
        sendMessageToController(m2);
    }

    public void runReceiver() throws Exception
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Consumer: " + id + " Starting iteration......" + "\n");
        }
        resetCounters();
        receiver.waitforCompletion(config.getMsgCount());
        MapMessage m = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.RECEIVED_END_MSG.ordinal());
        sendMessageToController(m);
    }

    public void resetCounters()
    {
        reporter.clear();
    }

    public void sendResults() throws Exception
    {
        receiveFromController(OPCode.CONSUMER_STOP);
        reporter.report();

        MapMessage m  = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.RECEIVED_CONSUMER_STATS.ordinal());
        m.setDouble(AVG_LATENCY, reporter.getAvgLatency());
        m.setDouble(MIN_LATENCY, reporter.getMinLatency());
        m.setDouble(MAX_LATENCY, reporter.getMaxLatency());
        m.setDouble(STD_DEV, reporter.getStdDev());
        m.setDouble(CONS_RATE, reporter.getRate());
        m.setLong(MSG_COUNT, reporter.getSampleSize());
        sendMessageToController(m);

        reporter.log(new StringBuilder("Total Msgs Received : ").append(reporter.getSampleSize()).toString());
        reporter.log(new StringBuilder("Consumer rate       : ").
                append(config.getDecimalFormat().format(reporter.getRate())).
                append(" msg/sec").toString());
        reporter.log(new StringBuilder("Avg Latency         : ").
                append(config.getDecimalFormat().format(reporter.getAvgLatency())).
                append(" ms").toString());
        reporter.log(new StringBuilder("Min Latency         : ").
                append(config.getDecimalFormat().format(reporter.getMinLatency())).
                append(" ms").toString());
        reporter.log(new StringBuilder("Max Latency         : ").
                append(config.getDecimalFormat().format(reporter.getMaxLatency())).
                append(" ms").toString());
        if (config.isPrintStdDev())
        {
            reporter.log(new StringBuilder("Std Dev             : ").
                    append(reporter.getStdDev()).toString());
        }
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
                System.out.println("Consumer: " + id + " starting a new iteration ......\n");
                runReceiver();
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

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
    }

    public static void main(String[] args) throws Exception
    {
        TestConfiguration config = new JVMArgConfiguration();
        MercuryReporter reporter= new MercuryReporter(MercuryThroughputAndLatency.class,System.out,10,true);
        String scriptId = (args.length == 1) ? args[0] : "";
        int conCount = config.getConnectionCount();
        final CountDownLatch testCompleted = new CountDownLatch(conCount);
        for (int i=0; i < conCount; i++)
        {
            final MercuryConsumerController cons = new MercuryConsumerController(config, reporter, scriptId + i);
            Runnable r = new Runnable()
            {
                public void run()
                {
                    cons.run();
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
                throw new Error("Error creating consumer thread",e);
            }
            t.start();
        }
        testCompleted.await();
        reporter.log("Consumers have completed the test......\n");
    }
}