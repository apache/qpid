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
    public class AddressTests
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
        // Address test
        //
        [Test]
        public void AddressConstructor_Empty()
        {
            Address addr = new Address();
            
            StringAssert.IsMatch("", addr.ToStr());

            StringAssert.IsMatch("", addr.Name);
            StringAssert.IsMatch("", addr.Subject);
            Dictionary<string, object> opts = addr.Options;
            Assert.AreEqual(0, opts.Count);
            StringAssert.IsMatch("", addr.Type);
        }


        [Test]
        public void AddressConstructor_Name()
        {
            Address addr = new Address("name1");

            StringAssert.IsMatch("", addr.ToStr());

            StringAssert.IsMatch("name1", addr.Name);
            StringAssert.IsMatch("", addr.Subject);
            Dictionary<string, object> opts = addr.Options;
            Assert.AreEqual(0, opts.Count);
            StringAssert.IsMatch("", addr.Type);
        }


        [Test]
        public void AddressConstructor_NameSubjOpts()
        {
            Dictionary<string, object> options = new Dictionary<string, object>();
            options["one"] = 1;
            options["two"] = "two";

            Address addr = new Address("name2", "subj2", options);

            StringAssert.IsMatch("name2/subj2;{node:{type:}, one:1, two:two}", addr.ToStr());

            StringAssert.IsMatch("name2", addr.Name);
            StringAssert.IsMatch("subj2", addr.Subject);
            Dictionary<string, object> opts = addr.Options;
            Assert.AreEqual(3, opts.Count);
            StringAssert.IsMatch("", addr.Type);
        }

        [Test]
        public void AddressConstructor_NameSubjOptsType()
        {
            Dictionary<string, object> options = new Dictionary<string, object>();
            options["one"] = 1;
            options["two"] = "two";

            Address addr = new Address("name3", "subj3", options, "type3");

            StringAssert.IsMatch("name3/subj3;{node:{type:type3}, one:1, two:two}", addr.ToStr());

            StringAssert.IsMatch("name3", addr.Name);
            StringAssert.IsMatch("subj3", addr.Subject);
            Dictionary<string, object> opts = addr.Options;
            Assert.AreEqual(3, opts.Count);
            StringAssert.IsMatch("type3", addr.Type);
        }

        [Test]
        public void AddressProperty()
        {
            Dictionary<string, object> options = new Dictionary<string, object>();
            options["one"] = 1;
            options["two"] = "two";
            options["pi"] = 3.14159;
            Dictionary<string, object> opts;

            Address addr = new Address();

            addr.Name = "name4";

            StringAssert.IsMatch("name4", addr.Name);
            StringAssert.IsMatch("", addr.Subject);
            opts = addr.Options;
            Assert.AreEqual(0, opts.Count);
            StringAssert.IsMatch("", addr.Type);

            addr.Subject = "subject4";

            StringAssert.IsMatch("name4", addr.Name);
            StringAssert.IsMatch("subject4", addr.Subject);
            opts = addr.Options;
            Assert.AreEqual(0, opts.Count);
            StringAssert.IsMatch("", addr.Type);

            addr.Type = "type4";

            StringAssert.IsMatch("name4", addr.Name);
            StringAssert.IsMatch("subject4", addr.Subject);
            opts = addr.Options;
            Assert.AreEqual(1, opts.Count);
            StringAssert.IsMatch("type4", addr.Type);

            addr.Options = options;

            StringAssert.IsMatch("name4", addr.Name);
            StringAssert.IsMatch("subject4", addr.Subject);
            opts = addr.Options;
            Assert.AreEqual(3, opts.Count);
            StringAssert.IsMatch("", addr.Type);
        }

    }
}
