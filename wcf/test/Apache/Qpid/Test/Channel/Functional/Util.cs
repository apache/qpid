/*
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
*/

namespace Apache.Qpid.Test.Channel.Functional
{
    using System;
    using System.Collections.Generic;
    using System.IO;
    using System.ServiceModel;
    using System.ServiceModel.Channels;
    using Apache.Qpid.Channel;

    internal class Util
    {
        public static Dictionary<string, string> GetProperties(string path)
        {
            string fileData = string.Empty;
            using (StreamReader sr = new StreamReader(path))
            {
                fileData = sr.ReadToEnd().Replace("\r", string.Empty);
            }

            Dictionary<string, string> properties = new Dictionary<string, string>();
            string[] kvp;
            string[] records = fileData.Split("\n".ToCharArray());
            foreach (string record in records)
            {
                if (record[0] == '/' || record[0] == '*')
                {
                    continue;
                }

                kvp = record.Split("=".ToCharArray());
                properties.Add(kvp[0], kvp[1]);
            }

            return properties;
        }

        public static Binding GetBinding()
        {
            return new AmqpBinding();
        }

        public static Binding GetCustomBinding()
        {
            AmqpTransportBindingElement transportElement = new AmqpTransportBindingElement();
            RawMessageEncodingBindingElement encodingElement = new RawMessageEncodingBindingElement();
            transportElement.BrokerHost = "127.0.0.1";
            transportElement.TransferMode = TransferMode.Streamed;

            CustomBinding brokerBinding = new CustomBinding();
            brokerBinding.Elements.Add(encodingElement);
            brokerBinding.Elements.Add(transportElement);

            return brokerBinding;
        }

        public static int GetMessageCountFromQueue(string listenUri)
        {
            Message receivedMessage = null;
            int messageCount = 0;

            IChannelListener<IInputChannel> listener = Util.GetBinding().BuildChannelListener<IInputChannel>(new Uri(listenUri), new BindingParameterCollection());
            listener.Open();
            IInputChannel proxy = listener.AcceptChannel(TimeSpan.FromSeconds(10));
            proxy.Open();

            while (true)
            {
                try
                {
                    receivedMessage = proxy.Receive(TimeSpan.FromSeconds(3));
                }
                catch (Exception e)
                {
                    if (e.GetType() == typeof(TimeoutException))
                    {
                        break;
                    }
                    else
                    {
                        throw;
                    }
                }

                messageCount++;
            }

            listener.Close();
            return messageCount;
        }

        public static void PurgeQueue(string listenUri)
        {
            GetMessageCountFromQueue(listenUri);
        }

        public static bool CompareResults(List<string> expectedResults, List<string> actualResults)
        {
            IEnumerator<string> actualResultEnumerator = actualResults.GetEnumerator();
            IEnumerator<string> expectedResultEnumerator = expectedResults.GetEnumerator();

            bool expectedResultEnumeratorPosition = expectedResultEnumerator.MoveNext();
            bool actualResultEnumeratorPosition = actualResultEnumerator.MoveNext();

            while (true == actualResultEnumeratorPosition &&
                   true == expectedResultEnumeratorPosition)
            {
                string expectedResult = expectedResultEnumerator.Current;
                string actualResult = actualResultEnumerator.Current;

                if (expectedResult.Equals(actualResult) == false)
                {
                    Console.WriteLine("OrderedResultsComparator: Expected result '{0}', but got '{1}' instead.", expectedResult, actualResult);
                    return false;
                }

                expectedResultEnumeratorPosition = expectedResultEnumerator.MoveNext();
                actualResultEnumeratorPosition = actualResultEnumerator.MoveNext();
            }

            // if either of them has still more data left, its an error
            if (true == expectedResultEnumeratorPosition)
            {
                string expectedResult = expectedResultEnumerator.Current;
                Console.WriteLine("OrderedResultsComparator: Got fewer results than expected, first missing result: '{0}'", expectedResult);
                return false;
            }

            if (true == actualResultEnumeratorPosition)
            {
                string actualResult = actualResultEnumerator.Current;
                Console.WriteLine("OrderedResultsComparator: Got more results than expected, first extra result: '{0}'", actualResult);
                return false;
            }

            return true;
        }
    }
}
