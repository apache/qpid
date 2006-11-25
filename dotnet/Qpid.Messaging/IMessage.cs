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
namespace Qpid.Messaging
{   
    public interface IMessage
    {
        string ContentType { get; set;}
        string ContentEncoding { get; set; }
        string CorrelationId { get; set; }
        byte[] CorrelationIdAsBytes { get; set; }
        DeliveryMode DeliveryMode { get; set; }
        long Expiration { get; set; }
        string MessageId { get; set; }
        int Priority { get; set; }
        bool Redelivered { get; set; }
        string ReplyToExchangeName { get; set; }
        string ReplyToRoutingKey { get; set; }
        long Timestamp { get; set; }
        string Type { get; set; }
        IHeaders Headers { get; }

        // XXX: UserId?
        // XXX: AppId?
        // XXX: ClusterId?

        void Acknowledge();
        void ClearBody();
    }
}
