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

import java.io.FileWriter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;

import org.apache.qpid.client.message.AMQPEncodedMapMessage;

/**
 * The Controller coordinates a test run between a number
 * of producers and consumers, configured via -Dprod_count and -Dcons_count.
 *
 * It waits till all the producers and consumers have registered and then
 * conducts a warmup run. Once all consumers and producers have completed
 * the warmup run and is ready, it will conduct the actual test run and
 * collect all stats from the participants and calculates the system
 * throughput, the avg/min/max for producer rates, consumer rates and latency.
 *
 * These stats are then printed to std out.
 * The Controller also prints events to std out to give a running account
 * of the test run in progress. Ex registering of participants, starting warmup ..etc.
 * This allows a scripting tool to monitor the progress.
 *
 * The Controller can be run in two modes.
 * 1. A single test run (default) where it just runs until the message count specified
 * for the producers via -Dmsg_count is sent and received.
 *
 * 2. Time based, configured via -Dduration=x, where x is in mins.
 * In this mode, the Controller repeatedly cycles through the tests (after an initial
 * warmup run) until the desired time is reached. If a test run is in progress
 * and the time is up, it will allow the run the complete.
 *
 * After each iteration, the stats will be printed out in csv format to a separate log file.
 * System throughput is calculated as follows
 * totalMsgCount/(totalTestTime)
 */
public class PerfTestController extends PerfBase implements MessageListener
{
    enum TestMode { SINGLE_RUN, TIME_BASED };

    TestMode testMode = TestMode.SINGLE_RUN;

    long totalTestTime;

    private double avgSystemLatency = 0.0;
    private double minSystemLatency = Double.MAX_VALUE;
    private double maxSystemLatency = 0;
    private double avgSystemLatencyStdDev = 0.0;

    private double avgSystemConsRate = 0.0;
    private double maxSystemConsRate = 0.0;
    private double minSystemConsRate = Double.MAX_VALUE;

    private double avgSystemProdRate = 0.0;
    private double maxSystemProdRate = 0.0;
    private double minSystemProdRate = Double.MAX_VALUE;

    private long totalMsgCount = 0;
    private double totalSystemThroughput = 0.0;

    private int consumerCount = Integer.getInteger("cons_count", 1);
    private int producerCount = Integer.getInteger("prod_count", 1);
    private int duration = Integer.getInteger("duration", -1); // in mins
    private Map<String,MapMessage> consumers;
    private Map<String,MapMessage> producers;

    private CountDownLatch consRegistered;
    private CountDownLatch prodRegistered;
    private CountDownLatch consReady;
    private CountDownLatch prodReady;
    private CountDownLatch receivedEndMsg;
    private CountDownLatch receivedConsStats;
    private CountDownLatch receivedProdStats;

    private MessageConsumer consumer;
    private boolean printStdDev = false;
    FileWriter writer;

    public PerfTestController()
    {
        super("");
        consumers = new ConcurrentHashMap<String,MapMessage>(consumerCount);
        producers = new ConcurrentHashMap<String,MapMessage>(producerCount);

        consRegistered = new CountDownLatch(consumerCount);
        prodRegistered = new CountDownLatch(producerCount);
        consReady = new CountDownLatch(consumerCount);
        prodReady = new CountDownLatch(producerCount);
        printStdDev = params.isPrintStdDev();
        testMode = (duration == -1) ? TestMode.SINGLE_RUN : TestMode.TIME_BASED;
    }

    public void setUp() throws Exception
    {
        super.setUp();
        if (testMode == TestMode.TIME_BASED)
        {
            writer = new FileWriter("stats-csv.log");
        }
        consumer = controllerSession.createConsumer(controllerQueue);
        System.out.println("\nController: " + producerCount + " producers are expected");
        System.out.println("Controller: " + consumerCount + " consumers are expected \n");
        consumer.setMessageListener(this);
        consRegistered.await();
        prodRegistered.await();
        System.out.println("\nController: All producers and consumers have registered......\n");
    }

    public void warmup() throws Exception
    {
        System.out.println("Controller initiating warm up sequence......");
        sendMessageToNodes(OPCode.CONSUMER_STARTWARMUP,consumers.values());
        sendMessageToNodes(OPCode.PRODUCER_STARTWARMUP,producers.values());
        prodReady.await();
        consReady.await();
        System.out.println("\nController : All producers and consumers are ready to start the test......\n");
    }

    public void startTest() throws Exception
    {
        resetCounters();
        System.out.println("\nController Starting test......");
        long start = Clock.getTime();
        sendMessageToNodes(OPCode.PRODUCER_START,producers.values());
        receivedEndMsg.await();
        totalTestTime = Clock.getTime() - start;
        sendMessageToNodes(OPCode.CONSUMER_STOP,consumers.values());
        receivedProdStats.await();
        receivedConsStats.await();
    }

    public void resetCounters()
    {
        minSystemLatency = Double.MAX_VALUE;
        maxSystemLatency = 0;
        maxSystemConsRate = 0.0;
        minSystemConsRate = Double.MAX_VALUE;
        maxSystemProdRate = 0.0;
        minSystemProdRate = Double.MAX_VALUE;

        totalMsgCount = 0;

        receivedConsStats = new CountDownLatch(consumerCount);
        receivedProdStats = new CountDownLatch(producerCount);
        receivedEndMsg = new CountDownLatch(producerCount);
    }

    public void calcStats() throws Exception
    {
        double totLatency = 0.0;
        double totStdDev = 0.0;
        double totalConsRate = 0.0;
        double totalProdRate = 0.0;

        MapMessage conStat = null;  // for error handling
        try
        {
            for (MapMessage m: consumers.values())
            {
                conStat = m;
                minSystemLatency = Math.min(minSystemLatency,m.getDouble(MIN_LATENCY));
                maxSystemLatency = Math.max(maxSystemLatency,m.getDouble(MAX_LATENCY));
                totLatency = totLatency + m.getDouble(AVG_LATENCY);
                totStdDev = totStdDev + m.getDouble(STD_DEV);

                minSystemConsRate = Math.min(minSystemConsRate,m.getDouble(CONS_RATE));
                maxSystemConsRate = Math.max(maxSystemConsRate,m.getDouble(CONS_RATE));
                totalConsRate = totalConsRate + m.getDouble(CONS_RATE);

                totalMsgCount = totalMsgCount + m.getLong(MSG_COUNT);
            }
        }
        catch(Exception e)
        {
            System.out.println("Error calculating stats from Consumer : " + conStat);
        }


        MapMessage prodStat = null;  // for error handling
        try
        {
            for (MapMessage m: producers.values())
            {
                prodStat = m;
                minSystemProdRate = Math.min(minSystemProdRate,m.getDouble(PROD_RATE));
                maxSystemProdRate = Math.max(maxSystemProdRate,m.getDouble(PROD_RATE));
                totalProdRate = totalProdRate + m.getDouble(PROD_RATE);
            }
        }
        catch(Exception e)
        {
            System.out.println("Error calculating stats from Producer : " + conStat);
        }

        avgSystemLatency = totLatency/consumers.size();
        avgSystemLatencyStdDev = totStdDev/consumers.size();
        avgSystemConsRate = totalConsRate/consumers.size();
        avgSystemProdRate = totalProdRate/producers.size();

        System.out.println("Total test time     : " + totalTestTime + " in " + Clock.getPrecision());

        totalSystemThroughput = (totalMsgCount*Clock.convertToSecs()/totalTestTime);
    }

    public void printResults() throws Exception
    {
        System.out.println(new StringBuilder("Total Msgs Received : ").append(totalMsgCount).toString());
        System.out.println(new StringBuilder("System Throughput   : ").
                           append(df.format(totalSystemThroughput)).
                           append(" msg/sec").toString());
        System.out.println(new StringBuilder("Avg Consumer rate   : ").
                           append(df.format(avgSystemConsRate)).
                           append(" msg/sec").toString());
        System.out.println(new StringBuilder("Min Consumer rate   : ").
                           append(df.format(minSystemConsRate)).
                           append(" msg/sec").toString());
        System.out.println(new StringBuilder("Max Consumer rate   : ").
                           append(df.format(maxSystemConsRate)).
                           append(" msg/sec").toString());

        System.out.println(new StringBuilder("Avg Producer rate   : ").
                           append(df.format(avgSystemProdRate)).
                           append(" msg/sec").toString());
        System.out.println(new StringBuilder("Min Producer rate   : ").
                           append(df.format(minSystemProdRate)).
                           append(" msg/sec").toString());
        System.out.println(new StringBuilder("Max Producer rate   : ").
                           append(df.format(maxSystemProdRate)).
                           append(" msg/sec").toString());

        System.out.println(new StringBuilder("Avg System Latency  : ").
                           append(df.format(avgSystemLatency)).
                           append(" ms").toString());
        System.out.println(new StringBuilder("Min System Latency  : ").
                           append(df.format(minSystemLatency)).
                           append(" ms").toString());
        System.out.println(new StringBuilder("Max System Latency  : ").
                           append(df.format(maxSystemLatency)).
                           append(" ms").toString());
        if (printStdDev)
        {
            System.out.println(new StringBuilder("Avg System Std Dev  : ").
                               append(avgSystemLatencyStdDev));
        }
    }

    private synchronized void sendMessageToNodes(OPCode code,Collection<MapMessage> nodes) throws Exception
    {
        System.out.println("\nController: Sending code " + code);
        MessageProducer tmpProd = controllerSession.createProducer(null);
        MapMessage msg = controllerSession.createMapMessage();
        msg.setInt(CODE, code.ordinal());
        for (MapMessage node : nodes)
        {
            if (node.getString(REPLY_ADDR) == null)
            {
                System.out.println("REPLY_ADDR is null " + node);
            }
            else
            {
                System.out.println("Controller: Sending " + code + " to " + node.getString(REPLY_ADDR));
            }
            tmpProd.send(controllerSession.createQueue(node.getString(REPLY_ADDR)), msg);
        }
    }

    public void onMessage(Message msg)
    {
        try
        {
            MapMessage m = (MapMessage)msg;
            OPCode code = OPCode.values()[m.getInt(CODE)];

            System.out.println("\n---------Controller Received Code : " + code);
            System.out.println("---------Data : " + ((AMQPEncodedMapMessage)m).getMap());

            switch (code)
            {
            case REGISTER_CONSUMER :
                if (consRegistered.getCount() == 0)
                {
                    System.out.println("Warning : Expected number of consumers have already registered," +
                    		"ignoring extra consumer");
                    break;
                }
                consumers.put(m.getString(ID),m);
                consRegistered.countDown();
                break;

            case REGISTER_PRODUCER :
                if (prodRegistered.getCount() == 0)
                {
                    System.out.println("Warning : Expected number of producers have already registered," +
                            "ignoring extra producer");
                    break;
                }
                producers.put(m.getString(ID),m);
                prodRegistered.countDown();
                break;

            case CONSUMER_READY :
                consReady.countDown();
                break;

            case PRODUCER_READY :
                prodReady.countDown();
                break;

            case RECEIVED_END_MSG :
                receivedEndMsg.countDown();
                break;

            case RECEIVED_CONSUMER_STATS :
                consumers.put(m.getString(ID),m);
                receivedConsStats.countDown();
                break;

            case RECEIVED_PRODUCER_STATS :
                producers.put(m.getString(ID),m);
                receivedProdStats.countDown();
                break;

            default:
                throw new Exception("Invalid OPCode " + code);
            }
        }
        catch (Exception e)
        {
            handleError(e,"Error when receiving messages " + msg);
        }
    }

    public void run()
    {
        try
        {
            setUp();
            warmup();
            if (testMode == TestMode.SINGLE_RUN)
            {
                startTest();
                calcStats();
                printResults();
            }
            else
            {
                long startTime = Clock.getTime();
                long timeLimit = duration * 60 * 1000;  // duration is in mins.
                boolean nextIteration = true;
                while (nextIteration)
                {
                    startTest();
                    calcStats();
                    writeStatsToFile();
                    if (Clock.getTime() - startTime < timeLimit)
                    {
                        sendMessageToNodes(OPCode.CONTINUE_TEST,consumers.values());
                        sendMessageToNodes(OPCode.CONTINUE_TEST,producers.values());
                        nextIteration = true;
                    }
                    else
                    {
                        nextIteration = false;
                    }
                }
            }
            tearDown();

        }
        catch(Exception e)
        {
            handleError(e,"Error when running test");
        }
    }

    @Override
    public void tearDown() throws Exception {
        System.out.println("Controller: Completed the test......\n");
        if (testMode == TestMode.TIME_BASED)
        {
            writer.close();
        }
        sendMessageToNodes(OPCode.STOP_TEST,consumers.values());
        sendMessageToNodes(OPCode.STOP_TEST,producers.values());
        super.tearDown();
    }

    public void writeStatsToFile() throws Exception
    {
        writer.append(String.valueOf(totalMsgCount)).append(",");
        writer.append(df.format(totalSystemThroughput)).append(",");
        writer.append(df.format(avgSystemConsRate)).append(",");
        writer.append(df.format(minSystemConsRate)).append(",");
        writer.append(df.format(maxSystemConsRate)).append(",");
        writer.append(df.format(avgSystemProdRate)).append(",");
        writer.append(df.format(minSystemProdRate)).append(",");
        writer.append(df.format(maxSystemProdRate)).append(",");
        writer.append(df.format(avgSystemLatency)).append(",");
        writer.append(df.format(minSystemLatency)).append(",");
        writer.append(df.format(maxSystemLatency));
        if (printStdDev)
        {
            writer.append(",").append(String.valueOf(avgSystemLatencyStdDev));
        }
        writer.append("\n");
        writer.flush();
    }

    public static void main(String[] args)
    {
        PerfTestController controller = new PerfTestController();
        controller.run();
    }
}
