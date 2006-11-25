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
using System.Collections;
using System.Net;
using log4net;
using Qpid.Client.qms;

namespace Qpid.Client
{
    public class QpidConnectionInfo : ConnectionInfo
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(QpidConnectionInfo));

        string _username = "guest";
        string _password = "guest";
        string _virtualHost = "/default";

        string _failoverMethod = null;
        IDictionary _failoverOptions = new Hashtable();
        IDictionary _options = new Hashtable();
        IList _brokerInfos = new ArrayList(); // List<BrokerInfo>
        string _clientName = String.Format("{0}{1:G}", Dns.GetHostName(), DateTime.Now.Ticks);

        public string asUrl()
        {
            string result = "amqp://";
            foreach (BrokerInfo info in _brokerInfos)
            {
                result += info.ToString();
            }
            return result;
            
        }

        public string getFailoverMethod()
        {
            return _failoverMethod;
        }

        public string getFailoverOption(string key)
        {
            return (string) _failoverOptions[key];
        }

        public int getBrokerCount()
        {
            return _brokerInfos.Count;
        }

        public BrokerInfo GetBrokerDetails(int index)
        {
            return (BrokerInfo)_brokerInfos[index];
        }

        public void AddBrokerInfo(BrokerInfo brokerInfo)
        {
            if (!_brokerInfos.Contains(brokerInfo))
            {
                _brokerInfos.Add(brokerInfo);
            }
        }

        public IList GetAllBrokerInfos()
        {
            return _brokerInfos;
        }

        public string GetClientName()
        {
            return _clientName;
        }

        public void SetClientName(string clientName)
        {
            _clientName = clientName;
        }

        public string getUsername()
        {
            return _username;
        }

        public void setUsername(string username)
        {
            _username = username;
        }

        public string getPassword()
        {
            return _password;
        }

        public void setPassword(string password)
        {
            _password = password;
        }

        public string getVirtualHost()
        {
            return _virtualHost;
        }

        public void setVirtualHost(string virtualHost)
        {
            _virtualHost = virtualHost;
        }

        public string getOption(string key)
        {
            return (string) _options[key];
        }

        public void setOption(string key, string value)
        {
            _options[key] = value;
        }
        
        public override string ToString()
        {
            return asUrl();
        }
    }
}
