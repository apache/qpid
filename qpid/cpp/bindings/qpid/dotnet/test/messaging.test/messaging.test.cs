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

//
// Note:
//   NUnit tests require all libraries to be on the project path
//   or in the project working directory. If an unmanaged DLL
//   (boost_xxx, for instance) is missing then NUnit will give
//   the error message:
//        System.IO.FileNotFoundException : 
//           The specified module could not be found. 
//            (Exception from HRESULT: 0x8007007E)
//
//   Users may need to adjust this project's reference to the 
//   NUnit assembly.
//

namespace Org.Apache.Qpid.Messaging.UnitTest
{
    using System;
    using System.Collections.Generic;
    using System.Collections.ObjectModel;
    using Org.Apache.Qpid.Messaging;
    using NUnit.Framework;

    [TestFixture]
    public class BasicTests
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
        // Types representing amqp.map and amqp.list
        //
        [Test]
        public void TypeTestForDictionary()
        {
            Dictionary<string, object> dx = new Dictionary<string, object>();
            
            StringAssert.Contains("System.Collections.Generic.Dictionary`2[System.String,System.Object]", dx.GetType().ToString());
        }

        [Test]
        public void TypeTestForCollection()
        {
            Collection<object> cx = new Collection<object>();
            
            StringAssert.Contains("System.Collections.ObjectModel.Collection`1[System.Object]", cx.GetType().ToString());
        }
    }
}
