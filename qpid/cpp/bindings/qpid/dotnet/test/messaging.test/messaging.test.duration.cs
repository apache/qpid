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
    public class DurationTests
    {
        [SetUp]
        public void SetUp()
        {
        }

        [TearDown]
        public void TearDown()
        {
        }

        [Test]
        public void ValueOfUSERVALUE()
        {
            Duration myDuration = new Duration(1234);
            Assert.AreEqual(1234, myDuration.Milliseconds);
        }

        [Test]
        public void ValueOfFOREVER()
        {
            bool result = DurationConstants.FORVER.Milliseconds > 1.0E18;
            Assert.True(result);
        }

        [Test]
        public void ValueOfIMMEDIATE()
        {
            Assert.AreEqual(0, DurationConstants.IMMEDIATE.Milliseconds);
        }

        [Test]
        public void ValueOfSECOND()
        {
            Assert.AreEqual(1000, DurationConstants.SECOND.Milliseconds);
        }


        [Test]
        public void ValueOfMINUTE()
        {
            Assert.AreEqual(60000, DurationConstants.MINUTE.Milliseconds);
        }

        [Test]
        public void ValueOfDefaultIsFOREVER()
        {
            Duration isInfinite = new Duration();

            bool result = isInfinite.Milliseconds > 1.0E18;
            Assert.True(result);
        }

        [Test]
        public void ComputedValueFiveMinutes_1()
        {
            Duration fiveMinutes = new Duration(DurationConstants.MINUTE.Milliseconds * 5);
            Assert.AreEqual(5 * 60000, fiveMinutes.Milliseconds);
        }

        [Test]
        public void ComputedValueFiveMinutes_2()
        {
            Duration fiveMinutes = new Duration(5 * DurationConstants.MINUTE.Milliseconds);
            Assert.AreEqual(5 * 60000, fiveMinutes.Milliseconds);
        }
    }
}