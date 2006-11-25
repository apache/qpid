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
    public class FailoverMethodConstants
    {
        public const String ROUND_ROBIN = "roundrobin";
        public const String RANDOM = "random";
    }

    public interface FailoverMethod
    {
        /**
         * Reset the Failover to initial conditions
         */
        void reset();

        /**
         *  Check if failover is possible for this method
         *
         * @return true if failover is allowed
         */
        bool failoverAllowed();

        /**
         * Notification to the Failover method that a connection has been attained.
         */
        void attainedConnection();

        /**
         * If there is no current BrokerInfo the null will be returned.
         * @return The current BrokerDetail value to use
         */
        BrokerInfo GetCurrentBrokerInfo();

        /**
         *  Move to the next BrokerInfo if one is available.
         * @return the next BrokerDetail or null if there is none.
         */
        BrokerInfo getNextBrokerDetails();

        /**
         * Set the currently active broker to be the new value.
         * @param broker The new BrokerDetail value
         */
        void setBroker(BrokerInfo broker);

        /**
         * Set the retries for this method
         * @param maxRetries the maximum number of time to retry this Method
         */
        void setRetries(int maxRetries);

        /**
         * @return The name of this method for display purposes.
         */
        String methodName();
    }
}
