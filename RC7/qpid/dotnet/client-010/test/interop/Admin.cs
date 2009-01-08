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

using NUnit.Framework;
using org.apache.qpid.client;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace test.interop
{
    public class Admin:TestCase
    {
        private static readonly Logger _log = Logger.get(typeof(Admin));

        [Test]
        public void createSession()
        {
            _log.debug("Running: createSession");
            ClientSession ssn = Client.createSession(0);
            ssn.close();            
            // This test fails if an exception is thrown 
        }

        [Test]
        public void queueLifecycle()
        {
            _log.debug("Running: queueLifecycle");
            ClientSession ssn = Client.createSession(0);
            ssn.queueDeclare("queue1", null, null);
            ssn.sync();
            ssn.queueDelete("queue1");
            ssn.sync();
            try
            {
                ssn.exchangeBind("queue1", "amq.direct", "queue1", null);
                ssn.sync();
            }
            catch (SessionException e)
            {
              // as expected
            }           
            // This test fails if an exception is thrown 
        }

        [Test]
        public void exchangeCheck()
        {
            _log.debug("Running: exchangeCheck");           
            ClientSession ssn = Client.createSession(0);            
            ExchangeQueryResult query = (ExchangeQueryResult) ssn.exchangeQuery("amq.direct").Result;
            Assert.IsFalse(query.getNotFound());
            Assert.IsTrue(query.getDurable());
            query = (ExchangeQueryResult)ssn.exchangeQuery("amq.topic").Result;
            Assert.IsFalse(query.getNotFound());
            Assert.IsTrue(query.getDurable());           
            query = (ExchangeQueryResult) ssn.exchangeQuery("foo").Result;           
            Assert.IsTrue(query.getNotFound());
        }

        [Test]
        public void exchangeBind()
        {
            _log.debug("Running: exchangeBind");       
            ClientSession ssn = Client.createSession(0);
            ssn.queueDeclare("queue1", null, null);
            ssn.exchangeBind("queue1", "amq.direct", "queue1", null);
            // This test fails if an exception is thrown 
        }


    }
}
