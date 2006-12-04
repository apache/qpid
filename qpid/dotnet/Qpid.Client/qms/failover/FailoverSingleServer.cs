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

namespace Qpid.Client.qms.failover
{
    public class FailoverSingleServer : FailoverMethod
    {
        /** The default number of times to rety a conection to this server */
        public const int DEFAULT_SERVER_RETRIES = 1;

        /**
         * The details of the Single Server
         */
        private BrokerInfo _brokerDetail;

        /**
         * The number of times to retry connecting to the sever
         */
        private int _retries;

        /**
         * The current number of attempts made to the server
         */
        private int _currentRetries;


        public FailoverSingleServer(ConnectionInfo connectionDetails)
        {
            if (connectionDetails.GetBrokerCount() > 0)
            {
                setBroker(connectionDetails.GetBrokerInfo(0));
            }
            else
            {
                throw new ArgumentException("BrokerInfo details required for connection.");
            }
        }

        public FailoverSingleServer(BrokerInfo brokerDetail)
        {
            setBroker(brokerDetail);
        }

        public void reset()
        {
            _currentRetries = -1;
        }

        public bool failoverAllowed()
        {
            return _currentRetries < _retries;
        }

        public void attainedConnection()
        {
            reset();
        }

        public BrokerInfo GetCurrentBrokerInfo()
        {
           return _brokerDetail;
        }

        public BrokerInfo getNextBrokerDetails()
        {
            if (_currentRetries == _retries)
            {
                return null;
            }
            else
            {
                if (_currentRetries < _retries)
                {
                    _currentRetries ++;
                }

                return _brokerDetail;
            }
        }

        public void setBroker(BrokerInfo broker)
        {
            if (broker == null)
            {
                throw new ArgumentException("BrokerInfo details cannot be null");
            }
            _brokerDetail = broker;

            String retries = broker.getOption(BrokerInfoConstants.OPTIONS_RETRY);
            if (retries != null)
            {
                try
                {
                    _retries = int.Parse(retries);
                }
                catch (FormatException)
                {
                    _retries = DEFAULT_SERVER_RETRIES;
                }
            }
            else
            {
                _retries = DEFAULT_SERVER_RETRIES;
            }

            reset();
        }

        public void setRetries(int retries)
        {
            _retries = retries;
        }

        public String methodName()
        {
            return "Single Server";
        }

        public String toString()
        {
            return "SingleServer:\n"+
                    "Max Retries:"+_retries+
                    "\nCurrent Retry:"+_currentRetries+
                    "\n"+_brokerDetail+"\n";
        }

    }
}
