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
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging.SessionReceiver
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
        void SessionException(Exception exception);
    }

    
    /// <summary>
    /// EventEngine - wait for messages from the underlying C++ code.
    /// When available get them and deliver them via callback to our 
    /// client through the ISessionReceiver interface.
    /// This class consumes the thread that calls the Run() function.
    /// </summary>

    internal class EventEngine
    {
        private Session          session;
        private ISessionReceiver callback;
        private bool             keepRunning;

        public EventEngine(Session theSession, ISessionReceiver thecallback)
        {
            this.session  = theSession;
            this.callback = thecallback;
        }

        /// <summary>
        /// Function to call Session's nextReceiver, discover messages,
        /// and to deliver messages through the callback.
        /// </summary>
        public void Open()
        {
            Receiver rcvr;
            Message  msg;
            try
            {
                keepRunning = true;
                while (keepRunning)
                {
                    rcvr = session.NextReceiver(DurationConstants.SECOND);

                    if (null != rcvr)
                    {
                        if (keepRunning)
                        {
                            msg = rcvr.Fetch(DurationConstants.SECOND);
                            this.callback.SessionReceiver(rcvr, msg);
                        }
                    }
                    //else
                    //    receive timed out
                    //    EventEngine exits the nextReceiver() function periodically
                    //    in order to test the keepRunning flag
                }
            }
            catch (Exception e)
            {
                this.callback.SessionException(e);
            }

            // Private thread is now exiting.
        }

        /// <summary>
        /// Function to stop the EventEngine. Private thread will exit within
        /// one second.
        /// </summary>
        public void Close()
        {
            keepRunning = false;
        }
    }


    /// <summary>
    /// server is the class that users instantiate to connect a SessionReceiver
    /// callback to the stream of received messages received on a Session.
    /// </summary>
    public class CallbackServer
    {
        private EventEngine ee;

        /// <summary>
        /// Constructor for the server.
        /// </summary>
        /// <param name="session">The Session whose messages are collected.</param>
        /// <param name="callback">The user function call with each message.</param>
        /// 
        public CallbackServer(Session session, ISessionReceiver callback)
        {
            ee = new EventEngine(session, callback);

            new System.Threading.Thread(
                new System.Threading.ThreadStart(ee.Open)).Start();
        }

        /// <summary>
        /// Function to stop the server.
        /// </summary>
        public void Close()
        {
            ee.Close();
        }
    }
}
