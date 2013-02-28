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
using System.Collections.ObjectModel;
using Org.Apache.Qpid.Messaging;
using Org.Apache.Qpid.Messaging.SessionReceiver;

namespace Org.Apache.Qpid.Messaging.Examples
{
    /// <summary>
    /// A class with functions to display structured messages.
    /// </summary>
    public static class MessageViewer
    {
        /// <summary>
        /// A Function to display a amqp/map message packaged as a Dictionary.
        /// </summary>
        /// <param name="dict">The AMQP map</param>
        /// <param name="level">Nested depth</param>
        public static void ShowDictionary(Dictionary<string, object> dict, int level)
        {
            foreach (KeyValuePair<string, object> kvp in dict)
            {
                Console.Write(new string(' ', level * 4));

                if (QpidTypeCheck.ObjectIsMap(kvp.Value))
                {
                    Console.WriteLine("Key: {0}, Value: Dictionary", kvp.Key);
                    ShowDictionary((Dictionary<string, object>)kvp.Value, level + 1);
                }
                else if (QpidTypeCheck.ObjectIsList(kvp.Value))
                {
                    Console.WriteLine("Key: {0}, Value: List", kvp.Key);
                    ShowList((Collection<object>)kvp.Value, level + 1);
                }
                else
                    Console.WriteLine("Key: {0}, Value: {1}, Type: {2}",
                        kvp.Key, kvp.Value, kvp.Value.GetType().ToString());
            }
        }

        /// <summary>
        /// A function to display a ampq/list message packaged as a List.
        /// </summary>
        /// <param name="list">The AMQP list</param>
        /// <param name="level">Nested depth</param>
        public static void ShowList(Collection<object> list, int level)
        {
            foreach (object obj in list)
            {
                Console.Write(new string(' ', level * 4));

                if (QpidTypeCheck.ObjectIsMap(obj))
                {
                    Console.WriteLine("Dictionary");
                    ShowDictionary((Dictionary<string, object>)obj, level + 1);
                }
                else if (QpidTypeCheck.ObjectIsList(obj))
                {
                    Console.WriteLine("List");
                    ShowList((Collection<object>)obj, level + 1);
                }
                else
                    Console.WriteLine("Value: {0}, Type: {1}",
                        obj.ToString(), obj.GetType().ToString());
            }
        }

        /// <summary>
        /// A function to diplay a Message. The native Object type is
        /// decomposed into AMQP types.
        /// </summary>
        /// <param name="message">The Message</param>
        public static void ShowMessage(Message message)
        {
            if ("amqp/map" == message.ContentType)
            {
                Console.WriteLine("Received a Dictionary");
                Dictionary<string, object> content = new Dictionary<string, object>();
                message.GetContent(content);
                ShowDictionary(content, 0);
            }
            else if ("amqp/list" == message.ContentType)
            {
                Console.WriteLine("Received a List");
                Collection<object> content = new Collection<object>();
                message.GetContent(content);
                ShowList(content, 0);
            }
            else
            {
                Console.WriteLine("Received a String");
                Console.WriteLine(message.GetContent());
            }
        }
    }



    /// <summary>
    /// A model class to demonstrate how a user may use the Qpid Messaging
    /// interface to receive Session messages using a callback.
    /// </summary>
    class ReceiverProcess : ISessionReceiver
    {
        UInt32 messagesReceived = 0;

        /// <summary>
        /// SessionReceiver implements the ISessionReceiver interface.
        /// It is the callback function that receives all messages for a Session.
        /// It may be called any time server is running.
        /// It is always called on server's private thread.
        /// </summary>
        /// <param name="receiver">The Receiver associated with the message.</param>
        /// <param name="message">The Message</param>
        public void SessionReceiver(Receiver receiver, Message message)
        {
            //
            // Indicate message reception
            //
            Console.WriteLine("--- Message {0}", ++messagesReceived);

            //
            // Display the received message
            //
            MessageViewer.ShowMessage(message);

            //
            // Acknowledge the receipt of all received messages.
            //
            receiver.Session.Acknowledge();
        }


        /// <summary>
        /// SessionReceiver implements the ISessionReceiver interface.
        /// It is the exception function that receives all exception messages
        /// It may be called any time server is running.
        /// It is always called on server's private thread.
        /// After this is called then the sessionReceiver and private thread are closed.
        /// </summary>
        /// <param name="exception">The exception.</param>
        public void SessionException(Exception exception)
        {
            // A typical application will take more action here.
            Console.WriteLine("{0} Exception caught.", exception.ToString());
        }


        /// <summary>
        /// Usage
        /// </summary>
        /// <param name="url">Connection target</param>
        /// <param name="addr">Address: broker exchange + routing key</param>
        /// <param name="nSec">n seconds to keep callback open</param>
        static void usage(string url, string addr, int nSec)
        {

            Console.WriteLine("usage: {0} [url  [addr [nSec]]]",
                System.Diagnostics.Process.GetCurrentProcess().ProcessName);
            Console.WriteLine();
            Console.WriteLine("A program to connect to a broker and receive");
            Console.WriteLine("messages from a named exchange with a routing key.");
            Console.WriteLine("The receiver uses a session callback and keeps the callback");
            Console.WriteLine("server open for so many seconds.");
            Console.WriteLine("The details of the message body's types and values are shown.");
            Console.WriteLine();
            Console.WriteLine(" url  = target address for 'new Connection(url)'");
            Console.WriteLine(" addr = address for 'session.CreateReceiver(addr)'");
            Console.WriteLine(" nSec = time in seconds to keep the receiver callback open");
            Console.WriteLine();
            Console.WriteLine("Default values:");
            Console.WriteLine("  {0} {1} {2} {3}",
                System.Diagnostics.Process.GetCurrentProcess().ProcessName,
                url, addr, nSec);
        }


        /// <summary>
        /// A function to illustrate how to open a Session callback and
        /// receive messages.
        /// </summary>
        /// <param name="args">Main program arguments</param>
        public int TestProgram(string[] args)
        {
            string url = "amqp:tcp:localhost:5672";
            string addr = "amq.direct/map_example";
            int nSec = 30;
            string connectionOptions = "";

            if (1 == args.Length)
            {
                if (args[0].Equals("-h") || args[0].Equals("-H") || args[0].Equals("/?"))
                {
                    usage(url, addr, nSec);
                    return 1;
                }
            }

            if (args.Length > 0)
                url = args[0];
            if (args.Length > 1)
                addr = args[1];
            if (args.Length > 2)
                nSec = System.Convert.ToInt32(args[2]);
            if (args.Length > 3)
                connectionOptions = args[3];

            //
            // Create and open an AMQP connection to the broker URL
            //
            Connection connection = new Connection(url, connectionOptions);
            connection.Open();

            //
            // Create a session.
            //
            Session session = connection.CreateSession();

            //
            // Receive through callback
            //
            // Create callback server and implicitly start it
            //
            SessionReceiver.CallbackServer cbServer =
                new SessionReceiver.CallbackServer(session, this);

            //
            // The callback server is running and executing callbacks on a
            // separate thread.
            //

            //
            // Create a receiver for the direct exchange using the
            // routing key "map_example".
            //
            Receiver receiver = session.CreateReceiver(addr);

            //
            // Establish a capacity
            //
            receiver.Capacity = 100;

            //
            // Wait so many seconds for messages to arrive.
            //
            System.Threading.Thread.Sleep(nSec * 1000);   // in mS

            //
            // Stop the callback server.
            //
            cbServer.Close();

            //
            // Close the receiver and the connection.
            //
            try
            {
                receiver.Close();
                connection.Close();
            }
            catch (Exception exception)
            {
                // receiver or connection may throw if they closed in error.
                // A typical application will take more action here.
                Console.WriteLine("{0} Closing exception caught.", exception.ToString());
            }
            return 0;
        }
    }


    class MapCallbackReceiverMain
    {
        /// <summary>
        /// Main program
        /// </summary>
        /// <param name="args">Main prgram args</param>
        static int Main(string[] args)
        {
            // Invoke 'TestProgram' as non-static class.
            ReceiverProcess mainProc = new ReceiverProcess();

            int result = mainProc.TestProgram(args);

            return result;
        }
    }
}

