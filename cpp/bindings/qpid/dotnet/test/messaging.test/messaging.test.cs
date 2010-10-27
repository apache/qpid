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
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging
{
    class Program
    {
        static void Main(string[] args)
        {
            //
            // Duration test - stub until proper nunit tests are ready...

            Duration myDuration = new Duration(1234);

            Console.WriteLine("Duration should be : 1234, is : {0}",
                            myDuration.Milliseconds);

            Console.WriteLine("Duration FOREVER should be : 1.8x10^19 (realbig), is : {0}",
                            DurationConstants.FORVER.Milliseconds);

            Console.WriteLine("Duration IMMEDIATE should be : 0, is : {0}",
                            DurationConstants.IMMEDIATE.Milliseconds);

            Console.WriteLine("Duration SECOND should be : 1,000, is : {0}",
                            DurationConstants.SECOND.Milliseconds);

            Console.WriteLine("Duration MINUTE should be : 60,000, is : {0}",
                            DurationConstants.MINUTE.Milliseconds);

            Duration isInfinite = new Duration();

            Console.WriteLine("Duration() should be : realbig, is : {0}",
                            isInfinite.Milliseconds);

            Duration fiveMinutes = new Duration(DurationConstants.MINUTE.Milliseconds * 5);
            Console.WriteLine("Duration 5MINUTE should be : 300,000, is : {0}",
                            fiveMinutes.Milliseconds);

            Duration fiveSec = DurationConstants.SECOND * 5;
            Console.WriteLine("Duration 5SECOND should be : 5,000 is : {0}",
                            fiveSec.Milliseconds);
            //
            // and so on
            //

            Dictionary<string, object> dx = new Dictionary<string, object>();

            Console.WriteLine("Dictionary.GetType() {0}", dx.GetType());

            //
            // Address test
            //
            Address aEmpty = new Address();
            Address aStr   = new Address("rare");

            Dictionary<string, object> options = new Dictionary<string,object>();
            options["one"] = 1;
            options["two"] = "two";

            Address aSubj = new Address("rare2", "subj", options);

            Address aType = new Address ("check3", "subj", options, "hot");

            Console.WriteLine("aEmpty : {0}", aEmpty.ToStr());
            Console.WriteLine("aStr   : {0}", aStr.ToStr());
            Console.WriteLine("aSubj  : {0}", aSubj.ToStr());
            Console.WriteLine("aType  : {0}", aType.ToStr());

            //
            // Raw message data retrieval
            //

            Message m2 = new Message("rarey");
            UInt64 m2Size = m2.ContentSize;


            byte[] myRaw = new byte [m2Size];

            m2.GetContent(myRaw);
            Console.WriteLine("Got raw array size {0}", m2Size);
            for (UInt64 i = 0; i < m2Size; i++)
                Console.Write("{0} ", myRaw[i].ToString());
            Console.WriteLine();

            //
            // Raw message creation
            //
            byte[] rawData = new byte[10];
            for (byte i=0; i<10; i++)
                rawData[i] = i;
            Message m3 = new Message(rawData);

            byte[] rawDataReadback = new byte[m3.ContentSize];
            m3.GetContent(rawDataReadback);
            for (UInt64 i = 0; i < m3.ContentSize; i++)
                Console.Write("{0} ", rawDataReadback[i].ToString());
            Console.WriteLine();

            //
            // Raw message from array slice
            //
            byte[] rawData4 = new byte[256];
            for (int i = 0; i <= 255; i++)
                rawData4[i] = (byte)i;

            Message m4 = new Message(rawData4, 246, 10);

            byte[] rawDataReadback4 = new byte[m4.ContentSize];
            m4.GetContent(rawDataReadback4);
            for (UInt64 i = 0; i < m4.ContentSize; i++)
                Console.Write("{0} ", rawDataReadback4[i].ToString());
            Console.WriteLine();

            //
            // Set content from array slice
            //
            m4.SetContent(rawData4, 100, 5);

            byte[] rawDataReadback4a = new byte[m4.ContentSize];
            m4.GetContent(rawDataReadback4a);
            for (UInt64 i = 0; i < m4.ContentSize; i++)
                Console.Write("{0} ", rawDataReadback4a[i].ToString());
            Console.WriteLine();

            //
            // Guid factoids
            //
            Guid myGuid = new Guid("000102030405060708090a0b0c0d0e0f");
            System.Type typeP = myGuid.GetType();
            System.TypeCode typeCode = System.Type.GetTypeCode(typeP);

            Console.WriteLine("Guid Type = {0}, TypeCode = {1}",
                typeP.ToString(), typeCode.ToString());
            // typeP="System.Guid", typeCode="Object"
            byte[] guidReadback;
            guidReadback = myGuid.ToByteArray();

            Console.WriteLine("GuidReadback len = {0}", guidReadback.Length);

            //
            // Set/Get some properties of a message
            //
            Message msgGetSet = new Message("12345");

            msgGetSet.Subject = "Subject";
            msgGetSet.MessageId = "MessageId";
            msgGetSet.UserId = "UserId";
            msgGetSet.CorrelationId = "CorrelationId";
            msgGetSet.Ttl = DurationConstants.SECOND;
            msgGetSet.Priority = (byte)'z';
            msgGetSet.Durable = false;
            msgGetSet.Redelivered = true;

            Dictionary<string, object> props = new Dictionary<string,object>();
            props.Add("firstProperty", 1);
            props.Add("secondProperty", 2);
            msgGetSet.Properties = props;



            Console.WriteLine("Message Subject = {0}", msgGetSet.Subject);
            Console.WriteLine("Message ContentType = {0}", msgGetSet.ContentType);
            Console.WriteLine("Message MessageId = {0}", msgGetSet.MessageId);
            Console.WriteLine("Message UserId = {0}", msgGetSet.UserId);
            Console.WriteLine("Message CorrelationId = {0}", msgGetSet.CorrelationId);
            Console.WriteLine("Message Ttl mS = {0}", msgGetSet.Ttl.Milliseconds);
            Console.WriteLine("Message Priority = {0}", msgGetSet.Priority);
            Console.WriteLine("Message Durable = {0}", msgGetSet.Durable);
            Console.WriteLine("Message Redelivered = {0}", msgGetSet.Redelivered);

            Dictionary<string, object> gotProps = msgGetSet.Properties;
            foreach (KeyValuePair<string, object> kvp in gotProps)
            {
                Console.WriteLine("Message Property {0} = {1}", kvp.Key, kvp.Value.ToString());
            }

            Console.WriteLine("Cycle through a million address copy constructions...");
            Address a1 = new Address("abc");
            Address a2 = new Address("def");
            Address a3 = new Address(a2);
            for (int i = 0; i < 1000000; i++)
            {
                a1 = a2;
                a2 = a3;
                a3 = new Address(a1);
            }
            Console.WriteLine("                                                  ...done.");

            Console.WriteLine("Use each object's copy constructor");

            Address Address1 = new Address("abc");
            Address Address2 = new Address(Address1);

            Connection Connection1 = new Connection("abc");
            Connection Connection2 = new Connection(Connection1);

            Message Message1 = new Message("abc");
            Message Message2 = new Message(Message1);


        }
    }
}
