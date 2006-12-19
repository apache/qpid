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
using System.Net;
using NUnit.Framework;
using Qpid.Client.qms;

namespace Qpid.Client.Tests.url
{
    [TestFixture]
    public class connectionUrlTests
    {
        [Test]
        public void FailoverURL()
        {
            //String url = "amqp://ritchiem:bob@/temp?brokerlist='tcp://localhost:5672;tcp://fancyserver:3000/',failover='roundrobin'";
            String url = "amqp://ritchiem:bob@default/temp?brokerlist='tcp://localhost:5672;tcp://fancyserver:3000/',failover='roundrobin'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.AreEqual("roundrobin", connectionurl.GetFailoverMethod());
            Assert.IsTrue(connectionurl.GetUsername().Equals("ritchiem"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("bob"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));

            Assert.IsTrue(connectionurl.GetBrokerCount() == 2);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));
            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);

            service = connectionurl.GetBrokerInfo(1);

            Assert.IsTrue(service.getTransport().Equals("tcp"));
            Assert.IsTrue(service.getHost().Equals("fancyserver"));
            Assert.IsTrue(service.getPort() == 3000);

        }

        [Test]
        public void SingleTransportUsernamePasswordURL()
        {
            String url = "amqp://ritchiem:bob@default/temp?brokerlist='tcp://localhost:5672'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("ritchiem"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("bob"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));

            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));
            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);
        }

        [Test]
        public void SingleTransportUsernameBlankPasswordURL()
        {
            String url = "amqp://ritchiem:@default/temp?brokerlist='tcp://localhost:5672'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("ritchiem"));
            Assert.IsTrue(connectionurl.GetPassword().Equals(""));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));

            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));
            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);
        }

        [Test]
        public void FailedURLNullPassword()
        {
            String url = "amqp://ritchiem@default/temp?brokerlist='tcp://localhost:5672'";

            try
            {
                QpidConnectionInfo.FromUrl(url);
                Assert.Fail("URL has null password");
            }
            catch (UrlSyntaxException e)
            {
                Assert.AreEqual("Null password in user information not allowed.", e.Message);
                Assert.IsTrue(e.GetIndex() == 7);
            }
        }

        [Test]
        public void SingleTransportURL()
        {
            String url = "amqp://guest:guest@default/test?brokerlist='tcp://localhost:5672'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);


            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("guest"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("guest"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/test"));


            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);


            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));
            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);
        }

        [Test]
        public void SingleTransportWithClientURLURL()
        {
            String url = "amqp://guest:guest@clientname/temp?brokerlist='tcp://localhost:5672'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);


            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("guest"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("guest"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));
            Assert.IsTrue(connectionurl.GetClientName().Equals("clientname"));


            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);


            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));
            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);
        }

        [Test]
        public void SingleTransport1OptionURL()
        {
            String url = "amqp://guest:guest@default/temp?brokerlist='tcp://localhost:5672',routingkey='jim'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("guest"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("guest"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));


            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));

            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);
            Assert.IsTrue(connectionurl.GetOption("routingkey").Equals("jim"));
        }

        [Test]
        public void SingleTransportDefaultedBroker()
        {
            String url = "amqp://guest:guest@default/temp?brokerlist='localhost:'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("guest"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("guest"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));


            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));

            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);
        }

        [Test]
        public void SingleTransportMultiOptionURL()
        {
            String url = "amqp://guest:guest@default/temp?brokerlist='tcp://localhost:5672',routingkey='jim',timeout='200',immediatedelivery='true'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("guest"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("guest"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));

            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("tcp"));

            Assert.IsTrue(service.getHost().Equals("localhost"));
            Assert.IsTrue(service.getPort() == 5672);

            Assert.IsTrue(connectionurl.GetOption("routingkey").Equals("jim"));
            Assert.IsTrue(connectionurl.GetOption("timeout").Equals("200"));
            Assert.IsTrue(connectionurl.GetOption("immediatedelivery").Equals("true"));
        }

        [Test]
        public void SinglevmURL()
        {
            String url = "amqp://guest:guest@default/messages?brokerlist='vm://default:2'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod() == null);
            Assert.IsTrue(connectionurl.GetUsername().Equals("guest"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("guest"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/messages"));

            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("vm"));
            Assert.AreEqual("localhost", service.getHost());
            Assert.AreEqual(2, service.getPort());
        }

        [Test]
        public void FailoverVMURL()
        {
            String url = "amqp://ritchiem:bob@default/temp?brokerlist='vm://default:2;vm://default:3',failover='roundrobin'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetFailoverMethod().Equals("roundrobin"));
            Assert.IsTrue(connectionurl.GetUsername().Equals("ritchiem"));
            Assert.IsTrue(connectionurl.GetPassword().Equals("bob"));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/temp"));

            Assert.AreEqual(2, connectionurl.GetBrokerCount());

            BrokerInfo service = connectionurl.GetBrokerInfo(0);

            Assert.IsTrue(service.getTransport().Equals("vm"));
            Assert.AreEqual("localhost", service.getHost());
            Assert.IsTrue(service.getPort() == 2);

            service = connectionurl.GetBrokerInfo(1);
            Assert.IsTrue(service.getTransport().Equals("vm"));
            Assert.AreEqual("localhost", service.getHost());
            Assert.IsTrue(service.getPort() == 3);
        }

        [Test]
        public void NoVirtualHostURL()
        {
            String url = "amqp://user@default?brokerlist='tcp://localhost:5672'";

            try
            {
                QpidConnectionInfo.FromUrl(url);
                Assert.Fail("URL has no virtual host should not parse");
            }
            catch (UrlSyntaxException)
            {
                // This should occur.
            }
        }

        [Test]
        public void NoClientID()
        {
            String url = "amqp://user:@default/test?brokerlist='tcp://localhost:5672'";

            ConnectionInfo connectionurl = QpidConnectionInfo.FromUrl(url);

            Assert.IsTrue(connectionurl.GetUsername().Equals("user"));
            Assert.IsTrue(connectionurl.GetPassword().Equals(""));
            Assert.IsTrue(connectionurl.GetVirtualHost().Equals("/test"));
            Assert.IsTrue(connectionurl.GetClientName().StartsWith(Dns.GetHostName()));

            Assert.IsTrue(connectionurl.GetBrokerCount() == 1);
        }

        [Test]
        public void WrongOptionSeparatorInOptions()
        {
            String url = "amqp://guest:guest@default/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'+failover='roundrobin'";
            try
            {
                QpidConnectionInfo.FromUrl(url);
                Assert.Fail("URL Should not parse");
            }
            catch (UrlSyntaxException urise)
            {
                Assert.IsTrue(urise.Message.Equals("Unterminated option. Possible illegal option separator:'+'"));
            }

        }

        [Test]
        public void NoUserDetailsProvidedWithClientID()

        {
            String url = "amqp://clientID/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
            try
            {
                QpidConnectionInfo.FromUrl(url);
                Assert.Fail("URL Should not parse");
            }
            catch (UrlSyntaxException urise)
            {
                Assert.IsTrue(urise.Message.StartsWith("User information not found on url"));
            }

        }

        [Test]
        public void NoUserDetailsProvidedNOClientID()

        {
            String url = "amqp:///test@default?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
            try
            {
                QpidConnectionInfo.FromUrl(url);
                Assert.Fail("URL Should not parse");
            }
            catch (UrlSyntaxException urise)
            {

                Assert.IsTrue(urise.Message.StartsWith("User information not found on url"));
            }

        }

        [Test]
        public void CheckVirtualHostFormat()
        {
            String url = "amqp://guest:guest@default/t.-_+!=:?brokerlist='tcp://localhost:5672'";

            ConnectionInfo connection = QpidConnectionInfo.FromUrl(url);
            Assert.IsTrue(connection.GetVirtualHost().Equals("/t.-_+!=:"));
        }

        [Test]
        public void CheckDefaultPort()
        {
            String url = "amqp://guest:guest@default/test=:?brokerlist='tcp://localhost'";

            ConnectionInfo connection = QpidConnectionInfo.FromUrl(url);

            BrokerInfo broker = connection.GetBrokerInfo(0);
            Assert.IsTrue(broker.getPort() == BrokerInfoConstants.DEFAULT_PORT);

        }

        [Test]
        public void CheckMissingFinalQuote()
        {
            String url = "amqp://guest:guest@id/test" + "?brokerlist='tcp://localhost:5672";

            try
            {
                QpidConnectionInfo.FromUrl(url);
            }
            catch (UrlSyntaxException e)
            {
//                Assert.AreEqual("Unterminated option at index 32: brokerlist='tcp://localhost:5672",
//                    e.Message);
                Assert.AreEqual("Unterminated option", e.Message);
            }
        }
    }
}
