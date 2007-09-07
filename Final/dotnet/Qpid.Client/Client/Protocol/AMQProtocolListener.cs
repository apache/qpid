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
using System.Collections;
using System.Threading;
using log4net;
using Apache.Qpid.Client.Failover;
using Apache.Qpid.Client.Protocol.Listener;
using Apache.Qpid.Client.State;
using Apache.Qpid.Framing;

namespace Apache.Qpid.Client.Protocol
{
    public class AMQProtocolListener : IProtocolListener
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(AMQProtocolListener));

        /**
         * We create the failover handler when the session is created since it needs a reference to the IoSession in order
         * to be able to send errors during failover back to the client application. The session won't be available in the
         * case where we failing over due to a Connection.Redirect message from the broker.
         */
        private FailoverHandler _failoverHandler;

        /**
         * This flag is used to track whether failover is being attempted. It is used to prevent the application constantly
         * attempting failover where it is failing.
         */
        internal FailoverState _failoverState = FailoverState.NOT_STARTED;

        internal FailoverState FailoverState
        {
            get { return _failoverState; }
            set { _failoverState = value; }
        }

        internal ManualResetEvent FailoverLatch;

        AMQConnection _connection;
        AMQStateManager _stateManager;

        public AMQStateManager StateManager
        {
            get { return _stateManager; }
            set { _stateManager = value; }
        }

        //private readonly CopyOnWriteArraySet _frameListeners = new CopyOnWriteArraySet();
        private readonly ArrayList _frameListeners = ArrayList.Synchronized(new ArrayList());
        
        AMQProtocolSession _protocolSession = null; // FIXME
        public AMQProtocolSession ProtocolSession { set { _protocolSession = value; } } // FIXME: can this be fixed?
        

        private readonly Object _lock = new Object();

        public AMQProtocolListener(AMQConnection connection, AMQStateManager stateManager)
        {
            _connection = connection;
            _stateManager = stateManager;
            _failoverHandler = new FailoverHandler(connection);
        }

        public void OnMessage(IDataBlock message)
        {
            // Handle incorrect protocol version.
            if (message is ProtocolInitiation)
            {
                string error = String.Format("Protocol mismatch - {0}", message.ToString());
                AMQException e = new AMQProtocolHeaderException(error);
                _log.Error("Closing connection because of protocol mismatch", e);
                //_protocolSession.CloseProtocolSession();
                _stateManager.Error(e);
                return;
            }

            AMQFrame frame = (AMQFrame)message;

            if (frame.BodyFrame is AMQMethodBody)
            {
                if (_log.IsDebugEnabled)
                {
                    _log.Debug("Method frame received: " + frame);
                }
                AMQMethodEvent evt = new AMQMethodEvent(frame.Channel, (AMQMethodBody)frame.BodyFrame, _protocolSession);
                try
                {
                    bool wasAnyoneInterested = false;
                    lock (_frameListeners.SyncRoot)
                    {
                        foreach (IAMQMethodListener listener in _frameListeners)
                        {
                            wasAnyoneInterested = listener.MethodReceived(evt) || wasAnyoneInterested;
                        }
                    }
                    if (!wasAnyoneInterested)
                    {
                        throw new AMQException("AMQMethodEvent " + evt + " was not processed by any listener.");
                    }
                }
                catch (Exception e)
                {
                    foreach (IAMQMethodListener listener in _frameListeners)
                    {
                        listener.Error(e);
                    }
                }
            }
            else if (frame.BodyFrame is ContentHeaderBody)
            {
                _protocolSession.MessageContentHeaderReceived(frame.Channel,
                                                              (ContentHeaderBody)frame.BodyFrame);
            }
            else if (frame.BodyFrame is ContentBody)
            {
                _protocolSession.MessageContentBodyReceived(frame.Channel,
                                                            (ContentBody)frame.BodyFrame);
            }
            else if (frame.BodyFrame is HeartbeatBody)
            {
                _log.Debug("HeartBeat received");
            }
            //_connection.BytesReceived(_protocolSession.Channel.ReadBytes); // XXX: is this really useful?
        }

        public void OnException(Exception cause)
        {
            _log.Warn("Protocol Listener received exception", cause);
            lock (_lock)
            {
                if (_failoverState == FailoverState.NOT_STARTED)
                {
                    if (cause is AMQConnectionClosedException)
                    {
                        WhenClosed();
                    }
                }
                    // We reach this point if failover was attempted and failed therefore we need to let the calling app
                    // know since we cannot recover the situation.
                else if (_failoverState == FailoverState.FAILED)
                {
                    // we notify the state manager of the error in case we have any clients waiting on a state
                    // change. Those "waiters" will be interrupted and can handle the exception
                    AMQException amqe = new AMQException("Protocol handler error: " + cause, cause);
                    PropagateExceptionToWaiters(amqe);
                    _connection.ExceptionReceived(cause);
                }
            }
        }

        /**
         * When the broker connection dies we can either get sessionClosed() called or exceptionCaught() followed by
         * sessionClosed() depending on whether we were trying to send data at the time of failure.
         *
         * @param session
         * @throws Exception
         */
        void WhenClosed()
        {
            _connection.StopHeartBeatThread();

            // TODO: Server just closes session with no warning if auth fails.
            if (_connection.Closed)
            {
                _log.Info("Channel closed called by client");
            }
            else
            {
                _log.Info("Channel closed called with failover state currently " + _failoverState);

                // Reconnectablility was introduced here so as not to disturb the client as they have made their intentions
                // known through the policy settings.

                if ((_failoverState != FailoverState.IN_PROGRESS) && _connection.IsFailoverAllowed)
                {
                    _log.Info("FAILOVER STARTING");
                    if (_failoverState == FailoverState.NOT_STARTED)
                    {
                        _failoverState = FailoverState.IN_PROGRESS;
                        StartFailoverThread();
                    }
                    else
                    {
                        _log.Info("Not starting failover as state currently " + _failoverState);
                    }
                }
                else
                {
                    _log.Info("Failover not allowed by policy.");

                    if (_failoverState != FailoverState.IN_PROGRESS)
                    {
                        _log.Info("sessionClose() not allowed to failover");
                        _connection.ExceptionReceived(
                            new AMQDisconnectedException("Server closed connection and reconnection not permitted."));
                    }
                    else
                    {
                        _log.Info("sessionClose() failover in progress");
                    }
                }
            }

            _log.Info("Protocol Channel [" + this + "] closed");
        }

        /// <summary>
        /// There are two cases where we have other threads potentially blocking for events to be handled by this
        /// class. These are for the state manager (waiting for a state change) or a frame listener (waiting for a
        /// particular type of frame to arrive). When an error occurs we need to notify these waiters so that they can
        /// react appropriately.
        /// 
        /// <param name="e">the exception to propagate</param>
        /// </summary>
        public void PropagateExceptionToWaiters(Exception e)
        {
            // FIXME: not sure if required as StateManager is in _frameListeners. Probably something to do with fail-over.
            _stateManager.Error(e);
            lock ( _lock )
            {
               foreach ( IAMQMethodListener listener in _frameListeners )
               {
                  listener.Error(e);
               }
            }
        }

        public void AddFrameListener(IAMQMethodListener listener)
        {
           lock ( _lock )
           {
              _frameListeners.Add(listener);
           }
        }

        public void RemoveFrameListener(IAMQMethodListener listener)
        {
            if (_log.IsDebugEnabled)
            {
                _log.Debug("Removing frame listener: " + listener.ToString());
            }
            lock ( _lock )
            {
               _frameListeners.Remove(listener);
            }
        }

        public void BlockUntilNotFailingOver()
        {
            if (FailoverLatch != null)
            {
                FailoverLatch.WaitOne();
            }
        }

        /// <summary>
        ///  "Failover" for redirection.
        /// </summary>
        /// <param name="host"></param>
        /// <param name="port"></param>
        public void Failover(string host, int port)
        {
            _failoverHandler.setHost(host);
            _failoverHandler.setPort(port);
            // see javadoc for FailoverHandler to see rationale for separate thread
            StartFailoverThread();
        }

        private void StartFailoverThread()
        {
            Thread failoverThread = new Thread(new ThreadStart(_failoverHandler.Run));
            failoverThread.Name = "Failover";
            // Do not inherit daemon-ness from current thread as this can be a daemon
            // thread such as a AnonymousIoService thread.
            failoverThread.IsBackground = false;
            failoverThread.Start();
        }
    }
}
