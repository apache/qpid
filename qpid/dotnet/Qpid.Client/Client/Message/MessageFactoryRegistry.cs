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
using Qpid.Framing;
using Qpid.Messaging;

namespace Qpid.Client.Message
{
    public class MessageFactoryRegistry
    {
        private readonly Hashtable _mimeToFactoryMap = new Hashtable();

        public void RegisterFactory(string mimeType, IMessageFactory mf)
        {    
            if (mf == null)
            {
                throw new ArgumentNullException("Message factory");
            }
            if (mimeType == null)
            {
                throw new ArgumentNullException("mf");
            }
            _mimeToFactoryMap[mimeType] = mf;
        }

        public void DeregisterFactory(string mimeType)
        {
            _mimeToFactoryMap.Remove(mimeType);
        }

        /// <summary>
        /// Create a message. This looks up the MIME type from the content header and instantiates the appropriate
        /// concrete message type.
        /// </summary>
        /// <param name="messageNbr">the AMQ message id</param>
        /// <param name="redelivered">true if redelivered</param>
        /// <param name="contentHeader">the content header that was received</param>
        /// <param name="bodies">a list of ContentBody instances</param>
        /// <returns>the message.</returns>
        /// <exception cref="AMQException"/>
        /// <exception cref="QpidException"/>
        public AbstractQmsMessage CreateMessage(ulong messageNbr, bool redelivered,
                                                ContentHeaderBody contentHeader,
                                                IList bodies)
        {
            BasicContentHeaderProperties properties =  (BasicContentHeaderProperties) contentHeader.Properties;

            if (properties.ContentType == null)
            {
                properties.ContentType = "";
            }

            IMessageFactory mf = (IMessageFactory) _mimeToFactoryMap[properties.ContentType];
            if (mf == null)
            {
                throw new AMQException("Unsupport MIME type of " + properties.ContentType);
            }
            else
            {
                return mf.CreateMessage(messageNbr, redelivered, contentHeader, bodies);
            }
        }

        public AbstractQmsMessage CreateMessage(string mimeType)
        {
            if (mimeType == null)
            {
                throw new ArgumentNullException("Mime type must not be null");
            }
            IMessageFactory mf = (IMessageFactory) _mimeToFactoryMap[mimeType];
            if (mf == null)
            {
                throw new AMQException("Unsupport MIME type of " + mimeType);
            }
            else
            {
                return mf.CreateMessage();
            }
        }

        /// <summary>
        /// Construct a new registry with the default message factories registered
        /// </summary>
        /// <returns>a message factory registry</returns>
        public static MessageFactoryRegistry NewDefaultRegistry()
        {
            MessageFactoryRegistry mf = new MessageFactoryRegistry();
            mf.RegisterFactory("text/plain", new QpidTextMessageFactory());
            mf.RegisterFactory("text/xml", new QpidTextMessageFactory());
            mf.RegisterFactory("application/octet-stream", new QpidBytesMessageFactory());
            // TODO: use bytes message for default message factory            
            // MJA - just added this bit back in...
            mf.RegisterFactory("", new QpidBytesMessageFactory());
            return mf;
        }
    }
}

