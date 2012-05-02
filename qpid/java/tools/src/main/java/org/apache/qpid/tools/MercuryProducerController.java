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
import org.apache.qpid.tools.report.MercuryReporter.MercuryThroughput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class MercuryProducerController extends MercuryBase
{
    private static final Logger _logger = LoggerFactory.getLogger(MercuryProducerController.class);
    MercuryReporter reporter;
    QpidSend sender;

    public MercuryProducerController(TestConfiguration config, MercuryReporter reporter, String prefix)
    {
        super(config,prefix);
        this.reporter = reporter;
        System.out.println("Producer ID : " + id);
    }

    public void setUp() throws Exception
    {
        super.setUp();
        sender = new QpidSend(reporter,config, con,dest);
        sender.setUp();
        MapMessage m = controllerSession.createMapMessage();
        m.setInt(CODE, OPCode.REGISTER_PRODUCER.ordinal());
        sendMessageToController(m);
    }

    public void warmup()throws Exception
    {
        receiveFromController(OPCode.PRODUCER_STARTWARMUP);
        if (_logger.isInfoEnabled())
        {
            _logger.info("Producer: " + id + " Warming up......");
        }
        sender.send(config.getWarmupCount());
        sender.sendEndMessage();
    }

    public void runSender() throws Exception
    {
        resetCounters();
        receiveFromController(OPCode.PRODUCER_START);
        sender.send(config.getMsgCount());
    }

    public void resetCounters()
    {
        sender.resetCounters();
    }

    public void sendResults() throws Exception
    {
        MapMessage msg = controllerSession.createMapMessage();
        msg.setInt(CODE, OPCode.RECEIVED_PRODUCER_STATS.ordinal());
        msg.setDouble(PROD_RATE, reporter.getRate());
        sendMessageToController(msg);
        reporter.log(new StringBuilder("Producer rate: ").
                append(config.getDecimalFormat().format(reporter.getRate())).
                append(" msg/sec").
                toString());
    }

    @Override
    public void tearDown() throws Exception
    {
        sender.tearDown();
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
                if(_logger.isInfoEnabled())
                {
                    _logger.info("=========================================================\n");
                    _logger.info("Producer: " + id + " starting a new iteration ......\n");
                }
                runSender();
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
        if (!config.isExternalController())
        {
            final MercuryTestController controller = new MercuryTestController(config);
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

    public static void main(String[] args) throws Exception
    {
        TestConfiguration config = new JVMArgConfiguration();
        MercuryReporter reporter= new MercuryReporter(MercuryThroughput.class,System.out,10,true);
        String scriptId = (args.length == 1) ? args[0] : "";
        int conCount = config.getConnectionCount();
        final CountDownLatch testCompleted = new CountDownLatch(conCount);
        for (int i=0; i < conCount; i++)
        {
            final MercuryProducerController prod = new MercuryProducerController(config, reporter, scriptId + i);
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
        reporter.log("Producers have completed the test......");
    }
}