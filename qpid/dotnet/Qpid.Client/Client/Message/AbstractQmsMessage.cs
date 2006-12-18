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
using System.Text;
using log4net;
using Qpid.Framing;
using Qpid.Messaging;
using Qpid.Buffer;

namespace Qpid.Client.Message
{
    public abstract class AbstractQmsMessage : AMQMessage, IMessage
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(AbstractQmsMessage));

        protected bool _redelivered;

        protected ByteBuffer _data;
        protected bool _readableMessage = false;

#region new_java_ctrs

        protected AbstractQmsMessage(ByteBuffer data)
            : base(new BasicContentHeaderProperties())
        {
            _data = data;
            if (_data != null)
            {
                _data.acquire();
            }
            _readableMessage = (data != null);
        }

        protected AbstractQmsMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data)
            : this(contentHeader, deliveryTag)
        {
            _data = data;
            if (_data != null)
            {
                _data.acquire();
            }
            _readableMessage = data != null;
        }

        protected AbstractQmsMessage(BasicContentHeaderProperties contentHeader, long deliveryTag) : base(contentHeader, deliveryTag)
        {
        }

#endregion

        public string MessageId
        {
            get
            {
                if (ContentHeaderProperties.MessageId == null)
                {
                    ContentHeaderProperties.MessageId = "ID:" + DeliveryTag;
                }
                return ContentHeaderProperties.MessageId;
            }
            set
            {
                ContentHeaderProperties.MessageId = value;
            }


        }        

        public long Timestamp
        {
            get
            {
                // TODO: look at ulong/long choice
                return (long) ContentHeaderProperties.Timestamp;
            }
            set
            {
                ContentHeaderProperties.Timestamp = (ulong) value;
            }
        }        

        protected void CheckReadable()
        {
            if (!_readableMessage)
            {
                throw new MessageNotReadableException("You need to call reset() to make the message readable");
            }
        }

        public byte[] CorrelationIdAsBytes
        {
            get
            {            
                return Encoding.Default.GetBytes(ContentHeaderProperties.CorrelationId);
            }
            set
            {
                ContentHeaderProperties.CorrelationId = Encoding.Default.GetString(value);
            }
        }

        public string CorrelationId
        {
            get
            {
                return ContentHeaderProperties.CorrelationId;
            }
            set
            {
                ContentHeaderProperties.ContentType = value;
            }
        }
        
        struct Dest
        {
            public string ExchangeName;
            public string RoutingKey;

            public Dest(string exchangeName, string routingKey)
            {
                ExchangeName = exchangeName;
                RoutingKey = routingKey;
            }
        }

        public string ReplyToExchangeName
        {
            get
            {
                Dest dest = ReadReplyToHeader();
                return dest.ExchangeName;
            }
            set
            {
                Dest dest = ReadReplyToHeader();
                dest.ExchangeName = value;
                WriteReplyToHeader(dest);
            }
        }

        public string ReplyToRoutingKey
        {
            get
            {
                Dest dest = ReadReplyToHeader();
                return dest.RoutingKey;
            }
            set
            {
                Dest dest = ReadReplyToHeader();
                dest.RoutingKey = value;
                WriteReplyToHeader(dest);
            }
        }

        private Dest ReadReplyToHeader()
        {
            string replyToEncoding = ContentHeaderProperties.ReplyTo;
            if (replyToEncoding == null)
            {
                return new Dest();
            }
            else
            {
                string routingKey;
                string exchangeName = GetExchangeName(replyToEncoding, out routingKey);
                return new Dest(exchangeName, routingKey);                
            }            
        }
        
        private void WriteReplyToHeader(Dest dest)
        {
            string encodedDestination = string.Format("{0}:{1}", dest.ExchangeName, dest.RoutingKey);
            ContentHeaderProperties.ReplyTo = encodedDestination;            
        }

        private static string GetExchangeName(string replyToEncoding, out string routingKey)
        {
            string[] split = replyToEncoding.Split(new char[':']);
            if (_log.IsDebugEnabled)
            {
                _log.Debug(string.Format("replyToEncoding = '{0}'", replyToEncoding));
                _log.Debug(string.Format("split = {0}", split));
                _log.Debug(string.Format("split.Length = {0}", split.Length));                            
            }
            if (split.Length == 1)
            {
                // Using an alternative split implementation here since it appears that string.Split
                // is broken in .NET. It doesn't split when the first character is the delimiter.
                // Here we check for the first character being the delimiter. This handles the case
                // where ExchangeName is empty (i.e. sends will be to the default exchange).
                if (replyToEncoding[0] == ':')
                {
                    split = new string[2];
                    split[0] = null;
                    split[1] = replyToEncoding.Substring(1);
                    if (_log.IsDebugEnabled)
                    {
                        _log.Debug("Alternative split method...");
                        _log.Debug(string.Format("split = {0}", split));
                        _log.Debug(string.Format("split.Length = {0}", split.Length));                                    
                    }
                }
            }
            if (split.Length != 2)
            {
                throw new QpidException("Illegal value in ReplyTo property: " + replyToEncoding);
            }

            string exchangeName = split[0];
            routingKey = split[1];
            return exchangeName;
        }

        public DeliveryMode DeliveryMode
        {
            get
            {
                byte b = ContentHeaderProperties.DeliveryMode;
                switch (b)
                {
                    case 1:
                        return DeliveryMode.NonPersistent;
                    case 2:
                        return DeliveryMode.Persistent;
                    default:
                        throw new QpidException("Illegal value for delivery mode in content header properties");
                }                
            }
            set
            {
                ContentHeaderProperties.DeliveryMode = (byte)(value==DeliveryMode.NonPersistent?1:2);
            }
        }        

        public bool Redelivered
        {
            get
            {
                return _redelivered;
            }
            set
            {
                _redelivered = value;
            }
        }        

        public string Type
        {
            get
            {
                return MimeType;
            }            
            set
            {
                //MimeType = value;
            }
        }
        
        public long Expiration
        {
            get
            {
                return ContentHeaderProperties.Expiration;
            }
            set
            {
                ContentHeaderProperties.Expiration = (uint) value;
            }
        }

        public int Priority
        {
            get
            {
                return ContentHeaderProperties.Priority;
            }
            set
            {
                ContentHeaderProperties.Priority = (byte) value;
            }
        }

        // FIXME: implement
        public string ContentType
        {
            get { throw new NotImplementedException(); }
            set { throw new NotImplementedException(); }
        }

        // FIXME: implement
        public string ContentEncoding
        {
            get { throw new NotImplementedException(); }
            set { throw new NotImplementedException(); }
        }

        public void Acknowledge()
        {
            // the JMS 1.1 spec says in section 3.6 that calls to acknowledge are ignored when client acknowledge
            // is not specified. In our case, we only set the session field where client acknowledge mode is specified.
            if (_channel != null)
            {
                // we set multiple to true here since acknowledgement implies acknowledge of all previous messages
                // received on the session
                _channel.AcknowledgeMessage((ulong)DeliveryTag, true);
            }

        }

        public IHeaders Headers
        {
            get { return new QpidHeaders(this); }
        }

        public abstract void ClearBodyImpl();

        public void ClearBody()
        {
            ClearBodyImpl();
            _readableMessage = false;
        }

        /// <summary>
        /// Get a String representation of the body of the message. Used in the
        /// toString() method which outputs this before message properties.
        /// </summary>
        /// <exception cref="QpidException"></exception>
        public abstract string ToBodyString();

        /// <summary>
        /// Return the raw byte array that is used to populate the frame when sending
        /// the message.
        /// </summary>
        /// <value>a byte array of message data</value>                
        public ByteBuffer Data
        {
            get
            {
                if (_data != null)
                {
                    if (!_readableMessage)
                    {
                        _data.flip();
                    }
                    else
                    {
                        // Make sure we rewind the data just in case any method has moved the
                        // position beyond the start.
                        _data.rewind();
                    }
                }
                return _data;
            }

            set
            {
                _data = value;
            }
        }

        public abstract string MimeType
        {
            get;           
        }

        public override string ToString()
        {
            try
            {
                StringBuilder buf = new StringBuilder("Body:\n");
                buf.Append(ToBodyString());
                buf.Append("\nQmsTimestamp: ").Append(Timestamp);
                buf.Append("\nQmsExpiration: ").Append(Expiration);
                buf.Append("\nQmsPriority: ").Append(Priority);
                buf.Append("\nQmsDeliveryMode: ").Append(DeliveryMode);
                buf.Append("\nReplyToExchangeName: ").Append(ReplyToExchangeName);
                buf.Append("\nReplyToRoutingKey: ").Append(ReplyToRoutingKey);
                buf.Append("\nAMQ message number: ").Append(DeliveryTag);
                buf.Append("\nProperties:");
                if (ContentHeaderProperties.Headers == null)
                {
                    buf.Append("<NONE>");
                }
                else
                {
                    buf.Append(Headers.ToString());
                }
                return buf.ToString();
            }
            catch (Exception e)
            {
                return e.ToString();
            }
        }

        public IFieldTable UnderlyingMessagePropertiesMap
        {
            get
            {
                return ContentHeaderProperties.Headers;
            }
            set
            {
                ContentHeaderProperties.Headers = (FieldTable)value;
            }
        }
        
        public FieldTable PopulateHeadersFromMessageProperties()
        {
            if (ContentHeaderProperties.Headers == null)
            {
                return null;
            }
            else
            {
                //
                // We need to convert every property into a String representation
                // Note that type information is preserved in the property name
                //
                FieldTable table = new FieldTable();
                foreach (DictionaryEntry entry in  ContentHeaderProperties.Headers)
                {                    
                    string propertyName = (string) entry.Key;
                    if (propertyName == null)
                    {
                        continue;
                    }
                    else
                    {
                        table[propertyName] = entry.Value.ToString();
                    }
                }
                return table;
            }
        }

        public BasicContentHeaderProperties ContentHeaderProperties
        {
            get
            {
                return (BasicContentHeaderProperties) _contentHeaderProperties;
            }
        }

        protected void Reset()
        {
            _readableMessage = true;
        }

        public bool IsReadable
        {
            get { return _readableMessage; }
        }

        public bool isWritable
        {
            get { return !_readableMessage; }
        }
    }
}
