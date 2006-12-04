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
using System.Collections;

namespace Qpid.Client.qms
{
    class ConnectionUrlConstants 
    {
        public const string AMQ_PROTOCOL = "amqp";
        public const string OPTIONS_BROKERLIST = "brokerlist";
        public const string OPTIONS_FAILOVER = "failover";
        public const string OPTIONS_FAILOVER_CYCLE = "cyclecount";
        public const string OPTIONS_SSL = "ssl";
    }

    /**
     Connection URL format
     amqp://[user:pass@][clientid]/virtualhost?brokerlist='tcp://host:port?option=\'value\'&option=\'value\';vm://:3/virtualpath?option=\'value\''&failover='method?option=\'value\'&option='value''"
     Options are of course optional except for requiring a single broker in the broker list.
     The option seperator is defined to be either '&' or ','
      */
    public interface ConnectionInfo
    {
        string AsUrl();

        string GetFailoverMethod();
        void SetFailoverMethod(string failoverMethod);

        string GetFailoverOption(string key);

        int GetBrokerCount();

        BrokerInfo GetBrokerInfo(int index);

        void AddBrokerInfo(BrokerInfo broker);

        IList GetAllBrokerInfos();

        string GetClientName();

        void SetClientName(string clientName);

        string GetUsername();

        void setUsername(string username);

        string GetPassword();

        void SetPassword(string password);

        string GetVirtualHost();

        void SetVirtualHost(string virtualHost);

        string GetOption(string key);

        void SetOption(string key, string value);
    }
}
