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

namespace Org.Apache.Qpid.Messaging.UnitTest
{
    using System;
    using System.Collections.Generic;
    using System.Collections.ObjectModel;
    using Org.Apache.Qpid.Messaging;
    using NUnit.Framework;

    [TestFixture]
    public class ConnectionTests
    {
        [SetUp]
        public void SetUp()
        {
        }

        [TearDown]
        public void TearDown()
        {
        }

        //
        // Doing without a real connection
        //
        [Test]
        public void ConnectionCreate_1()
        {
            Connection myConn = new Connection("url");
            Assert.IsFalse(myConn.IsOpen);
        }

        [Test]
        public void ConnectionCreate_2()
        {
            Dictionary<string, object> options = new Dictionary<string, object>();
            options["reconnect"] = true;

            Connection myConn = new Connection("url", options);
            Assert.IsFalse(myConn.IsOpen);
        }

        [Test]
        public void ConnectionCreate_3()
        {
            Connection myConn = new Connection("url", "{reconnect:True}");
            Assert.IsFalse(myConn.IsOpen);
        }

        [Test]
        public void ConnectionSetOption()
        {
            Dictionary<string, object> options = new Dictionary<string, object>();
            options["reconnect"] = true;

            Connection myConn = new Connection("url", options);
            myConn.SetOption("reconnect", false);

            Assert.IsFalse(myConn.IsOpen);
        }

        [Test]
        public void ConnectionClose()
        {
            Dictionary<string, object> options = new Dictionary<string, object>();

            Connection myConn = new Connection("url", options);
            myConn.Close();

            Assert.IsFalse(myConn.IsOpen);
        }
    }
}
