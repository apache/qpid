/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.failover;

import org.apache.log4j.Logger;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.vmbroker.AMQVMBrokerCreationException;

public class FailoverBrokerTester implements Runnable
{
    private static final Logger _logger = Logger.getLogger(FailoverBrokerTester.class);

    private int[] _brokers;
    private int[] _brokersKilling;
    private long _delayBeforeKillingStart;
    private long _delayBetweenCullings;
    private long _delayBetweenRecreates;
    private boolean _recreateBrokers;
    private long _delayBeforeReCreationStart;

    private volatile boolean RUNNING;


    /**
     * An InVM Broker Tester. Creates then kills VM brokers to allow failover testing.
     *
     * @param brokerCount The number of brokers to create
     * @param delay       The delay before and between broker killings
     */
    public FailoverBrokerTester(int brokerCount, long delay)
    {
        this(brokerCount, delay, delay, false, 0, 0);
    }

    /**
     * An InVM Broker Tester. Creates then kills VM brokers to allow failover testing.
     *
     * @param brokerCount                The number of brokers
     * @param delayBeforeKillingStart
     * @param delayBetweenCullings
     * @param recreateBrokers
     * @param delayBeforeReCreationStart
     * @param delayBetweenRecreates
     */
    public FailoverBrokerTester(int brokerCount, long delayBeforeKillingStart,
                                long delayBetweenCullings, boolean recreateBrokers,
                                long delayBeforeReCreationStart, long delayBetweenRecreates)
    {
        int[] brokers = new int[brokerCount];

        for (int n = 0; n < brokerCount; n++)
        {
            brokers[n] = n + 1;
        }
        _brokersKilling = _brokers = brokers;
        _recreateBrokers = recreateBrokers;
        _delayBeforeKillingStart = delayBeforeKillingStart;
        _delayBetweenCullings = delayBetweenCullings;
        _delayBetweenRecreates = delayBetweenRecreates;
        _delayBeforeReCreationStart = delayBeforeReCreationStart;

        createWorld();
    }

    /**
     * An InVM Broker Tester. Creates then kills VM brokers to allow failover testing.
     *
     * @param brokerArray                Array for broker creation and killing order
     * @param delayBeforeKillingStart
     * @param delayBetweenCullings
     * @param recreateBrokers
     * @param delayBeforeReCreationStart
     * @param delayBetweenRecreates
     */
    public FailoverBrokerTester(int[] brokerArray, long delayBeforeKillingStart,
                                long delayBetweenCullings, boolean recreateBrokers,
                                long delayBeforeReCreationStart, long delayBetweenRecreates)
    {
        _brokersKilling = _brokers = brokerArray;
        _recreateBrokers = recreateBrokers;
        _delayBeforeKillingStart = delayBeforeKillingStart;
        _delayBetweenCullings = delayBetweenCullings;
        _delayBetweenRecreates = delayBetweenRecreates;
        _delayBeforeReCreationStart = delayBeforeReCreationStart;

        createWorld();
    }

    /**
     * An InVM Broker Tester. Creates then kills VM brokers to allow failover testing.
     *
     * @param brokerCreateOrder          Array for broker creation order
     * @param brokerKillOrder            Array for broker killing order
     * @param delayBeforeKillingStart
     * @param delayBetweenCullings
     * @param recreateBrokers
     * @param delayBeforeReCreationStart
     * @param delayBetweenRecreates
     */
    public FailoverBrokerTester(int[] brokerCreateOrder, int[] brokerKillOrder, long delayBeforeKillingStart,
                                long delayBetweenCullings, boolean recreateBrokers,
                                long delayBeforeReCreationStart, long delayBetweenRecreates)
    {
        _brokers = brokerCreateOrder;
        _brokersKilling = brokerKillOrder;
        _recreateBrokers = recreateBrokers;
        _delayBeforeKillingStart = delayBeforeKillingStart;
        _delayBetweenCullings = delayBetweenCullings;
        _delayBetweenRecreates = delayBetweenRecreates;
        _delayBeforeReCreationStart = delayBeforeReCreationStart;

        createWorld();
    }

    private void createWorld()
    {
        System.setProperty("amqj.NoAutoCreateVMBroker", "true");

        genesis();

        Thread brokerGod = new Thread(this);
        brokerGod.setName("Broker God");
        brokerGod.start();
    }


    private void genesis()
    {
        _logger.info("Creating " + _brokers.length + " VM Brokers.");
        for (int count = 0; count < _brokers.length; count++)
        {
            try
            {
                TransportConnection.createVMBroker(_brokers[count]);
            }
            catch (AMQVMBrokerCreationException e)
            {
                ;
            }
        }
    }

    public void run()
    {

        RUNNING = true;
        try
        {
            _logger.info("Sleeping before culling starts.");
            Thread.sleep(_delayBeforeKillingStart);
        }
        catch (InterruptedException e)
        {
            _logger.info("Interupted sleeping before killing starts.");
        }

        Thread brokerGod = new Thread(new BrokerDestroyer());
        brokerGod.setName("Broker Destroyer");
        brokerGod.start();

        if (_recreateBrokers)
        {
            try
            {
                _logger.info("Sleeping before recreation starts.");
                Thread.sleep(_delayBeforeReCreationStart - _delayBeforeKillingStart);
            }
            catch (InterruptedException e)
            {
                _logger.info("Interupted sleeping before recreation starts.");
            }

            brokerGod = new Thread(new BrokerCreator());
            brokerGod.setName("Broker Creator");
            brokerGod.start();
        }
    }


    public void stopTesting()
    {
        _logger.info("Stopping Broker Tester.");
        RUNNING = false;
    }

    class BrokerCreator implements Runnable
    {
        public void run()
        {
            _logger.info("Created Broker Creator.");
            while (RUNNING)
            {
                for (int count = 0; count < _brokers.length; count++)
                {
                    try
                    {
                        _logger.info("Creating Broker:" + _brokers[count]);
                        TransportConnection.createVMBroker(_brokers[count]);
                    }
                    catch (AMQVMBrokerCreationException e)
                    {
                        _logger.info("Unable to recreate broker:" + count + ", Port:" + _brokers[count]);
                    }
                    try
                    {
                        Thread.sleep(_delayBetweenRecreates);
                    }
                    catch (InterruptedException e)
                    {
                        _logger.info("Interupted between broker recreates.");
                    }
                }
            }
            _logger.info("Ending Broker Creator.");
        }
    }

    class BrokerDestroyer implements Runnable
    {
        public void run()
        {
            _logger.info("Created Broker Destroyer.");
            while (RUNNING)
            {
                for (int count = 0; count < _brokersKilling.length; count++)
                {
                    _logger.info("Destroying Broker:" + _brokersKilling[count]);
                    killNextBroker(_brokersKilling[count], _delayBetweenCullings);
                }
            }
            _logger.info("Ending Broker Destroyer.");
        }

        private void killNextBroker(int broker, long delay)
        {

            //Kill the broker
            TransportConnection.killVMBroker(broker);

            //Give the client time to get up and going
            try
            {
                Thread.sleep(delay);
            }
            catch (InterruptedException e)
            {
                _logger.info("Sleeping before broker killing was interrupted,");
            }


        }
    }


}
