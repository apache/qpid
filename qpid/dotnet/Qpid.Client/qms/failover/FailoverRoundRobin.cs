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
using System;
using System.Text;
using log4net;

namespace Qpid.Client.qms.failover
{
    public class FailoverRoundRobin : FailoverMethod
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(FailoverRoundRobin));

        /** The default number of times to cycle through all servers */
        public const int DEFAULT_CYCLE_RETRIES = 0;
        /** The default number of times to retry each server */
        public const int DEFAULT_SERVER_RETRIES = 0;

        /**
         * The index into the hostDetails array of the broker to which we are connected
         */
        private int _currentBrokerIndex = -1;

        /**
         * The number of times to retry connecting for each server
         */
        private int _serverRetries;

        /**
         * The current number of retry attempts made
         */
        private int _currentServerRetry;

        /**
         *  The number of times to cycle through the servers
         */
        private int _cycleRetries;

        /**
         * The current number of cycles performed.
         */
        private int _currentCycleRetries;

        /**
         * Array of BrokerDetail used to make connections.
         */
        private ConnectionInfo _connectionDetails;

        public FailoverRoundRobin(ConnectionInfo connectionDetails)
        {
            if (!(connectionDetails.getBrokerCount() > 0))
            {
                throw new ArgumentException("At least one broker details must be specified.");
            }

            _connectionDetails = connectionDetails;

            //There is no current broker at startup so set it to -1.
            _currentBrokerIndex = -1;

            String cycleRetries = _connectionDetails.getFailoverOption(ConnectionUrlConstants.OPTIONS_FAILOVER_CYCLE);

            if (cycleRetries != null)
            {
                try
                {
                    _cycleRetries = int.Parse(cycleRetries);
                }
                catch (FormatException)
                {
                    _cycleRetries = DEFAULT_CYCLE_RETRIES;
                }
            }

            _currentCycleRetries = 0;

            _serverRetries = 0;
            _currentServerRetry = -1;
        }

        public void reset()
        {
            _currentBrokerIndex = 0;
            _currentCycleRetries = 0;
            _currentServerRetry = -1;
        }

        public bool failoverAllowed()
        {
            return ((_currentCycleRetries < _cycleRetries)
                   || (_currentServerRetry < _serverRetries)
                   || (_currentBrokerIndex < (_connectionDetails.getBrokerCount() - 1)));
        }

        public void attainedConnection()
        {
            _currentCycleRetries = 0;
            _currentServerRetry = -1;
        }

        public BrokerInfo GetCurrentBrokerInfo()
        {
            if (_currentBrokerIndex == -1)
            {
                return null;
            }

            return _connectionDetails.GetBrokerDetails(_currentBrokerIndex);
        }

        public BrokerInfo getNextBrokerDetails()
        {
            if (_currentBrokerIndex == (_connectionDetails.getBrokerCount() - 1))
            {
                if (_currentServerRetry < _serverRetries)
                {
                    if (_currentBrokerIndex == -1)
                    {
                        _currentBrokerIndex = 0;

                        setBroker(_connectionDetails.GetBrokerDetails(_currentBrokerIndex ));

                        _logger.Info("First Run using " + _connectionDetails.GetBrokerDetails(_currentBrokerIndex));
                    }
                    else
                    {
                        _logger.Info("Retrying " + _connectionDetails.GetBrokerDetails(_currentBrokerIndex));
                    }

                    _currentServerRetry++;
                }
                else
                {
                    _currentCycleRetries++;
                    //failed to connect to first broker
                    _currentBrokerIndex = 0;

                    setBroker(_connectionDetails.GetBrokerDetails(_currentBrokerIndex ));

                    // This is zero rather than -1 as we are already retrieving the details.
                    _currentServerRetry = 0;
                }
                //else - should force client to stop as max retries has been reached.
            }
            else
            {
                if (_currentServerRetry < _serverRetries)
                {
                    if (_currentBrokerIndex == -1)
                    {
                        _currentBrokerIndex = 0;

                        setBroker(_connectionDetails.GetBrokerDetails(_currentBrokerIndex ));

                        _logger.Info("First Run using " + _connectionDetails.GetBrokerDetails(_currentBrokerIndex));
                    }
                    else
                    {
                        _logger.Info("Retrying " + _connectionDetails.GetBrokerDetails(_currentBrokerIndex));
                    }
                    _currentServerRetry++;
                }
                else
                {
                    _currentBrokerIndex++;

                     setBroker(_connectionDetails.GetBrokerDetails(_currentBrokerIndex ));
                    // This is zero rather than -1 as we are already retrieving the details.
                    _currentServerRetry = 0;
                }
            }

            return _connectionDetails.GetBrokerDetails(_currentBrokerIndex);
        }

        public void setBroker(BrokerInfo broker)
        {
            _connectionDetails.AddBrokerInfo(broker);

            int index = _connectionDetails.GetAllBrokerInfos().IndexOf(broker);

            String serverRetries = broker.getOption(BrokerDetailsConstants.OPTIONS_RETRY);

            if (serverRetries != null)
            {
                try
                {
                    _serverRetries = int.Parse(serverRetries);
                }
                catch (FormatException)
                {
                    _serverRetries = DEFAULT_SERVER_RETRIES;
                }
            }

            _currentServerRetry = -1;
            _currentBrokerIndex = index;
        }

        public void setRetries(int maxRetries)
        {
            _cycleRetries = maxRetries;
        }

        public String methodName()
        {
            return "Cycle Servers";
        }

        public override string ToString()
        {
            StringBuilder sb = new StringBuilder();

            sb.Append(GetType().Name).Append("\n");

            sb.Append("Broker count: ").Append(_connectionDetails.getBrokerCount());
            sb.Append("\ncurrent broker index: ").Append(_currentBrokerIndex);

            sb.Append("\nCycle Retries: ").Append(_cycleRetries);
            sb.Append("\nCurrent Cycle:").Append(_currentCycleRetries);
            sb.Append("\nServer Retries:").Append(_serverRetries);
            sb.Append("\nCurrent Retry:").Append(_currentServerRetry);
            sb.Append("\n");

            for(int i=0; i < _connectionDetails.getBrokerCount() ; i++)
            {
                if (i == _currentBrokerIndex)
                {
                    sb.Append(">");
                }
                sb.Append(_connectionDetails.GetBrokerDetails(i));
                sb.Append("\n");
            }

            return sb.ToString();
        }
    }
}
