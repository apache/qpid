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
using org.apache.qpid.messaging;

namespace org.apache.qpid.messaging.sessionreceiver
{
    /// <summary>
    /// ISessionReceiver interface defines the callback for users to supply.
    /// Once established this callback will receive all messages for all 
    /// receivers defined by the current session.
    /// Users are expected not to 'fetch' or 'get' messages by any other means.
    /// Users must acknowledge() the Session's messages either in the callback
    /// function or by some other scheme.
    /// </summary>

    public interface ISessionReceiver
    {
        void SessionReceiver(Receiver receiver, Message message);
    }

    
    /// <summary>
    /// eventEngine - wait for messages from the underlying C++ code.
    /// When available get them and deliver them via callback to our 
    /// client through the ISessionReceiver interface.
    /// This class consumes the thread that calls the Run() function.
    /// </summary>

    internal class eventEngine
    {
        private Session          session;
        private ISessionReceiver callback;
        private bool             keepRunning;

        public eventEngine(Session theSession, ISessionReceiver thecallback)
        {
            this.session  = theSession;
            this.callback = thecallback;
        }

        /// <summary>
        /// Function to call Session's nextReceiver, discover messages,
        /// and to deliver messages through the callback.
        /// </summary>
        public void open()
        {
            Receiver rcvr = session.createReceiver();
            Message  msg;

            keepRunning = true;
            while (keepRunning)
            {
                if (session.nextReceiver(rcvr, DurationConstants.SECOND))
                {
                    if (keepRunning)
                    {
                        msg = rcvr.fetch(DurationConstants.SECOND);
                        this.callback.SessionReceiver(rcvr, msg);
                    }
                }
                //else
                //    receive timed out
                //    eventEngine exits the nextReceiver() function periodically
                //    in order to test the keepRunning flag
            }
            // Private thread is now exiting.
        }

        /// <summary>
        /// Function to stop the eventEngine. Private thread will exit within
        /// one second.
        /// </summary>
        public void close()
        {
            keepRunning = false;
        }
    }


    /// <summary>
    /// server is the class that users instantiate to connect a SessionReceiver
    /// callback to the stream of received messages received on a Session.
    /// </summary>
    public class server
    {
        private eventEngine ee;

        /// <summary>
        /// Constructor for the server.
        /// </summary>
        /// <param name="session">The Session whose messages are collected.</param>
        /// <param name="callback">The user function call with each message.</param>
        /// 
        public server(Session session, ISessionReceiver callback)
        {
            ee = new eventEngine(session, callback);

            new System.Threading.Thread(
                new System.Threading.ThreadStart(ee.open)).Start();
        }

        /// <summary>
        /// Function to stop the server.
        /// </summary>
        public void close()
        {
            ee.close();
        }
    }
}
