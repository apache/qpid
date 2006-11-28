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
using System.Threading;
using log4net;
using Qpid.Client.Message;
using Qpid.Collections;
using Qpid.Framing;
using Qpid.Messaging;

namespace Qpid.Client
{
    public class BasicMessageConsumer : Closeable, IMessageConsumer
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(BasicMessageConsumer));

        private bool _noLocal;

        /**
         * We store the exclusive field in order to be able to reuse it when resubscribing in the event of failover
         */
        private bool _exclusive;

        public bool Exclusive
        {
            get { return _exclusive; }
        }

        public bool NoLocal
        {
            get { return _noLocal; }
            set { _noLocal = value; }
        }

        AcknowledgeMode _acknowledgeMode = AcknowledgeMode.NoAcknowledge;

        public AcknowledgeMode AcknowledgeMode
        {
            get { return _acknowledgeMode; }
        }

        private MessageReceivedDelegate _messageListener;

        /// <summary>
        /// The consumer tag allows us to close the consumer by sending a jmsCancel method to the
        /// broker
        /// </summary>
        private string _consumerTag;

        /// <summary>
        /// We need to know the channel id when constructing frames
        /// </summary>
        private ushort _channelId;

        private readonly string _queueName;

        /// <summary>
        /// Protects the setting of a messageListener
        /// </summary>
        private readonly object _syncLock = new object();

        /**
         * We store the prefetch field in order to be able to reuse it when resubscribing in the event of failover
         */
        private int _prefetch;

        /// <summary>
        /// When true indicates that either a message listener is set or that
        /// a blocking receive call is in progress
        /// </summary>
        private bool _receiving;

        /// <summary>
        /// Used in the blocking receive methods to receive a message from
        /// the Channel thread. Argument true indicates we want strict FIFO semantics
        /// </summary>
        private readonly SynchronousQueue _synchronousQueue = new SynchronousQueue(true);

        private MessageFactoryRegistry _messageFactory;

        private AmqChannel _channel;

        public BasicMessageConsumer(ushort channelId, string queueName, bool noLocal,
                                    MessageFactoryRegistry messageFactory, AmqChannel channel)
        {
            _channelId = channelId;
            _queueName = queueName;
            _noLocal = noLocal;
            _messageFactory = messageFactory;
            _channel = channel;
        }

        #region IMessageConsumer Members

        public MessageReceivedDelegate OnMessage
        {
            get
            {
                return _messageListener;
            }
            set
            {
                CheckNotClosed();

                lock (_syncLock)
                {
                    // If someone is already receiving
                    if (_messageListener != null && _receiving)
                    {
                        throw new InvalidOperationException("Another thread is already receiving...");
                    }

                    _messageListener = value;

                    _receiving = (_messageListener != null);

                    if (_receiving)
                    {
                        _logger.Debug("Message listener set for queue with name " + _queueName);
                    }
                }
            }
        }

        public IMessage Receive(long delay)
        {
            CheckNotClosed();

            lock (_syncLock)
            {
                // If someone is already receiving
                if (_receiving)
                {
                    throw new InvalidOperationException("Another thread is already receiving (possibly asynchronously)...");
                }

                _receiving = true;
            }

            try
            {
                object o;
                if (delay > 0)
                {
                    //o = _synchronousQueue.Poll(l, TimeUnit.MILLISECONDS);
                    throw new NotImplementedException("Need to implement synchronousQueue.Poll(timeout");
                }
                else
                {
                    o = _synchronousQueue.DequeueBlocking();
                }
                return ReturnMessageOrThrow(o);
            }
            finally
            {
                lock (_syncLock)
                {
                    _receiving = false;
                }
            }
        }

        public IMessage Receive()
        {
            return Receive(0);
        }

        public IMessage ReceiveNoWait()
        {
            CheckNotClosed();

            lock (_syncLock)
            {
                // If someone is already receiving
                if (_receiving)
                {
                    throw new InvalidOperationException("Another thread is already receiving (possibly asynchronously)...");
                }

                _receiving = true;
            }

            try
            {
                object o = _synchronousQueue.Dequeue();
                return ReturnMessageOrThrow(o);
            }
            finally
            {
                lock (_syncLock)
                {
                    _receiving = false;
                }
            }
        }

        #endregion

        /// <summary>
        /// We can get back either a Message or an exception from the queue. This method examines the argument and deals
        /// with it by throwing it (if an exception) or returning it (in any other case).
        /// </summary>
        /// <param name="o">the object off the queue</param>
        /// <returns> a message only if o is a Message</returns>
        /// <exception>JMSException if the argument is a throwable. If it is a QpidMessagingException it is rethrown as is, but if not
        /// a QpidMessagingException is created with the linked exception set appropriately</exception>
        private IMessage ReturnMessageOrThrow(object o)                
        {
            // errors are passed via the queue too since there is no way of interrupting the poll() via the API.
            if (o is Exception)
            {
                Exception e = (Exception) o;
                throw new QpidException("Message consumer forcibly closed due to error: " + e, e);
            }
            else
            {
                return (IMessage) o;
            }
        }

        #region IDisposable Members

        public void Dispose()
        {
            Close();
        }

        #endregion

        public override void Close()
        {
            // FIXME: Don't we need FailoverSupport here (as we have SyncWrite). i.e. rather than just locking FailOverMutex
            lock (_channel.Connection.FailoverMutex)
            {
                lock (_closingLock)
                {
                    Interlocked.Exchange(ref _closed, CLOSED);

                    AMQFrame cancelFrame = BasicCancelBody.CreateAMQFrame(_channelId, _consumerTag, false);

                    try
                    {
                        _channel.Connection.ConvenientProtocolWriter.SyncWrite(
                            cancelFrame, typeof(BasicCancelOkBody));
                    }
                    catch (AMQException e)
                    {
                        _logger.Error("Error closing consumer: " + e, e);
                        throw new QpidException("Error closing consumer: " + e);
                    }
                    finally
                    {
                        DeregisterConsumer();
                    }
                }
            }
        }

        /// <summary>
        /// Called from the AmqChannel when a message has arrived for this consumer. This methods handles both the case
        /// of a message listener or a synchronous receive() caller.
        /// </summary>
        /// <param name="messageFrame">the raw unprocessed mesage</param>
        /// <param name="acknowledgeMode">the acknowledge mode requested for this message</param>
        /// <param name="channelId">channel on which this message was sent</param>       
        internal void NotifyMessage(UnprocessedMessage messageFrame, AcknowledgeMode acknowledgeMode, ushort channelId)
        {
            if (_logger.IsDebugEnabled)
            {
                _logger.Debug("notifyMessage called with message number " + messageFrame.DeliverBody.DeliveryTag);
            }
            try
            {
                AbstractQmsMessage jmsMessage = _messageFactory.CreateMessage(messageFrame.DeliverBody.DeliveryTag,
                                                                              messageFrame.DeliverBody.Redelivered,
                                                                              messageFrame.ContentHeader,
                                                                              messageFrame.Bodies);

                /*if (acknowledgeMode == AcknowledgeMode.PreAcknowledge)
                {
                    _channel.sendAcknowledgement(messageFrame.deliverBody.deliveryTag);
                }*/
                if (acknowledgeMode == AcknowledgeMode.ClientAcknowledge)
                {
                    // we set the session so that when the user calls acknowledge() it can call the method on session
                    // to send out the appropriate frame
                    jmsMessage.Channel = _channel;
                }

                lock (_syncLock)
                {
                    if (_messageListener != null)
                    {
#if __MonoCS__
                        _messageListener(jmsMessage);
#else
                        _messageListener.Invoke(jmsMessage);
#endif
                    }
                    else
                    {
                        _synchronousQueue.Enqueue(jmsMessage);
                    }
                }
                if (acknowledgeMode == AcknowledgeMode.AutoAcknowledge)
                {
                    _channel.SendAcknowledgement(messageFrame.DeliverBody.DeliveryTag);
                }
            }
            catch (Exception e)
            {
                _logger.Error("Caught exception (dump follows) - ignoring...", e);
            }
        }

        internal void NotifyError(Exception cause)
        {
            lock (_syncLock)
            {
                SetClosed();

                // we have no way of propagating the exception to a message listener - a JMS limitation - so we
                // deal with the case where we have a synchronous receive() waiting for a message to arrive
                if (_messageListener == null)
                {
                    // offer only succeeds if there is a thread waiting for an item from the queue
                    if (_synchronousQueue.EnqueueNoThrow(cause))
                    {
                        _logger.Debug("Passed exception to synchronous queue for propagation to receive()");
                    }
                }
                DeregisterConsumer();
            }
        }

        private void SetClosed()
        {
            Interlocked.Exchange(ref _closed, CLOSED);
        }

        /// <summary>
        /// Perform cleanup to deregister this consumer. This occurs when closing the consumer in both the clean
        /// case and in the case of an error occurring.
        /// </summary>
        internal void DeregisterConsumer()
        {
            _channel.DeregisterConsumer(_consumerTag);
        }

        public string ConsumerTag
        {
            get
            {
                return _consumerTag;
            }
            set
            {
                _consumerTag = value;
            }
        }

        /**
         * Called when you need to invalidate a consumer. Used for example when failover has occurred and the
         * client has vetoed automatic resubscription.
         * The caller must hold the failover mutex.
         */
        internal void MarkClosed()
        {
            SetClosed();
            DeregisterConsumer();
        }

        public int Prefetch
        {
            get { return _prefetch; }
        }

        public string QueueName
        {
            get { return _queueName; }
        }
    }
}
