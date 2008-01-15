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
using log4net;
using NUnit.Framework;
using Apache.Qpid.Messaging;
using Apache.Qpid.Client.Qms;
using Apache.Qpid.Client;

namespace Apache.Qpid.Integration.Tests.testcases
{
    /// <summary>
    /// Provides a basis for writing Unit tests that communicate with an AMQ protocol broker. By default it creates a connection
    /// to a message broker running on localhost on the standard AMQ port, 5672, using guest:guest login credentials, on the default exchange,
    /// 'test' queue.
    /// </summary>
    public class BaseMessagingTestFixture
    {
        private static ILog _logger = LogManager.GetLogger(typeof(BaseMessagingTestFixture));

        /// <summary> The default AMQ connection URL to use for tests. </summary>
        const string connectionUri = "amqp://guest:guest@default/test?brokerlist='tcp://localhost:5672'";

        /// <summary> Holds the test connection. </summary>
        protected IConnection _connection;

        /// <summary> Holds the test channel. </summary>
        protected IChannel _channel;

        /// <summary>
        /// Creates the test connection and channel.
        /// </summary>
        [SetUp]
        public virtual void Init()
        {
            _logger.Info("public virtual void Init(): called");

            try
            {
                IConnectionInfo connectionInfo = QpidConnectionInfo.FromUrl(connectionUri);               
                _connection = new AMQConnection(connectionInfo);
                _channel = _connection.CreateChannel(false, AcknowledgeMode.NoAcknowledge, 500, 300);
            }
            catch (QpidException e)
            {
                _logger.Error("Error initialisng test fixture: " + e, e);
                throw e;
            }
        }

        /// <summary>
        /// Disposes the test connection. This is called manually because the connection is a field so dispose will not be automatically 
        /// called on it.
        /// </summary>
        [TearDown]
        public virtual void Shutdown()
        {
            _logger.Info("public virtual void Shutdown(): called");

            if (_connection != null)
            {
                _logger.Info("Disposing connection.");
                _connection.Dispose();
                _logger.Info("Connection disposed.");
            }
        }
    }
}
