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

namespace Qpid.Messaging
{
    public delegate void MessageReceivedDelegate(IMessage msg);

    public interface IChannel : IDisposable
    {
        AcknowledgeMode AcknowledgeMode { get; }
        bool Transacted { get; }

        /// <summary>
        /// Prefetch value to be used as the default for consumers created on this channel.
        /// </summary>
        int DefaultPrefetch
        {
            get;
            set;
        }

        void DeclareExchange(string exchangeName, string exchangeClass);
        void DeleteExchange(string exchangeName);

        void DeclareQueue(string queueName, bool isDurable, bool isExclusive, bool isAutoDelete);
        void DeleteQueue();

        string GenerateUniqueName();
        IFieldTable CreateFieldTable();

        void Bind(string queueName, string exchangeName, string routingKey);
        void Bind(string queueName, string exchangeName, string routingKey, IFieldTable args);

        IMessage CreateMessage();
        IBytesMessage CreateBytesMessage();
        ITextMessage CreateTextMessage();
        ITextMessage CreateTextMessage(string initialValue);

        #region Consuming

        MessageConsumerBuilder CreateConsumerBuilder(string queueName);

        IMessageConsumer CreateConsumer(string queueName,
                                        int prefetch,
                                        bool noLocal,
                                        bool exclusive,
                                        bool durable,
                                        string subscriptionName);

        void Unsubscribe(string subscriptionName);

        #endregion

        #region Publishing

        MessagePublisherBuilder CreatePublisherBuilder();

        IMessagePublisher CreatePublisher(string exchangeName,
                                        string routingKey,
                                        DeliveryMode deliveryMode,
                                        long timeToLive,
                                        bool immediate,
                                        bool mandatory,
                                        int priority);

        #endregion

        #region Transactions

        void Recover();
        void Commit();
        void Rollback();

        #endregion
    }
}
