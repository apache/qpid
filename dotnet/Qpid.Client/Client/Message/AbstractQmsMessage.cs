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
        protected bool _redelivered;

        protected ByteBuffer _data;
        protected bool _readableMessage = false;
        private QpidHeaders _headers;

#region new_java_ctrs

        protected AbstractQmsMessage(ByteBuffer data)
            : base(new BasicContentHeaderProperties())
        {
           Init(data);
        }

        protected AbstractQmsMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data)
            : this(contentHeader, deliveryTag)
        {
           Init(data);
        }

        protected AbstractQmsMessage(BasicContentHeaderProperties contentHeader, long deliveryTag) : base(contentHeader, deliveryTag)
        {
           Init(null);
        }

        private void Init(ByteBuffer data)
        {
           _data = data;
           if ( _data != null )
           {
              _data.Acquire();
           }
           _readableMessage = (data != null);
           if ( ContentHeaderProperties.Headers == null )
              ContentHeaderProperties.Headers = new FieldTable();
           _headers = new QpidHeaders(ContentHeaderProperties.Headers);
        }

#endregion

        #region Properties
        //
        // Properties
        //

        /// <summary>
        /// The application message identifier
        /// </summary>
        public string MessageId
        {
            get {
                if (ContentHeaderProperties.MessageId == null)
                {
                    ContentHeaderProperties.MessageId = "ID:" + DeliveryTag;
                }
                return ContentHeaderProperties.MessageId;
            }
            set { ContentHeaderProperties.MessageId = value; }
        }

        /// <summary>
        /// The message timestamp
        /// </summary>
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

        /// <summary>
        /// The <see cref="CorrelationId"/> as a byte array.
        /// </summary>
        public byte[] CorrelationIdAsBytes
        {
            get { return Encoding.Default.GetBytes(ContentHeaderProperties.CorrelationId); }
            set { ContentHeaderProperties.CorrelationId = Encoding.Default.GetString(value); }
        }

        /// <summary>
        /// The application correlation identifier
        /// </summary>
        public string CorrelationId
        {
            get { return ContentHeaderProperties.CorrelationId; }
            set { ContentHeaderProperties.CorrelationId = value; }
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

        /// <summary>
        /// Exchange name of the reply-to address
        /// </summary>
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

        /// <summary>
        /// Routing key of the reply-to address
        /// </summary>
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



        /// <summary>
        /// Non-persistent (1) or persistent (2)
        /// </summary>
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

       /// <summary>
       /// True, if this is a redelivered message
       /// </summary>
        public bool Redelivered
        {
            get { return _redelivered; }
            set { _redelivered = value; }
        }

        /// <summary>
        /// The message type name
        /// </summary>
        public string Type
        {
            get { return ContentHeaderProperties.Type; }
            set { ContentHeaderProperties.Type = value; }
        }

        /// <summary>
        /// Message expiration specification
        /// </summary>
        public long Expiration
        {
            get { return ContentHeaderProperties.Expiration; }
            set { ContentHeaderProperties.Expiration = value; }
        }

        /// <summary>
        /// The message priority, 0 to 9
        /// </summary>
        public byte Priority
        {
            get { return ContentHeaderProperties.Priority; }
            set { ContentHeaderProperties.Priority = (byte) value; }
        }

        /// <summary>
        /// The MIME Content Type
        /// </summary>
        public string ContentType
        {
            get { return ContentHeaderProperties.ContentType; }
            set { ContentHeaderProperties.ContentType = value; }
        }

        /// <summary>
        /// The MIME Content Encoding
        /// </summary>
        public string ContentEncoding
        {
            get { return ContentHeaderProperties.Encoding; }
            set { ContentHeaderProperties.Encoding = value; }
        }

        /// <summary>
        /// Headers of this message
        /// </summary>
        public IHeaders Headers
        {
            get { return _headers; }
        }

        /// <summary>
        /// The creating user id
        /// </summary>
        public string UserId
        {
            get { return ContentHeaderProperties.UserId; }
            set { ContentHeaderProperties.UserId = value; }
        }

        /// <summary>
        /// The creating application id
        /// </summary>
        public string AppId
        {
            get { return ContentHeaderProperties.AppId; }
            set { ContentHeaderProperties.AppId = value; }
        }

        /// <summary>
        /// Intra-cluster routing identifier
        /// </summary>
        public string ClusterId
        {
            get { return ContentHeaderProperties.ClusterId; }
            set { ContentHeaderProperties.ClusterId = value; }
        }

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
                        _data.Flip();
                    }
                    else
                    {
                        // Make sure we rewind the data just in case any method has moved the
                        // position beyond the start.
                        _data.Rewind();
                    }
                }
                return _data;
            }

            set
            {
                _data = value;
            }
        }
        #endregion // Properties


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

        protected virtual void Reset()
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

        protected void CheckReadable()
        {
           if ( !_readableMessage )
           {
              throw new MessageNotReadableException("You need to call reset() to make the message readable");
           }
        }

        /// <summary>
        /// Decodes the replyto field if one is set.
        /// 
        /// Splits a replyto field containing an exchange name followed by a ':', followed by a routing key into the exchange name and
        /// routing key seperately. The exchange name may be empty in which case the empty string is returned. If the exchange name is
        /// empty the replyto field is expected to being with ':'.
        /// 
        /// Anyhting other than a two part replyto field sperated with a ':' will result in an exception.
        /// </summary>
        /// 
        /// <returns>A destination initialized to the replyto location if a replyto field was set, or an empty destination otherwise.</returns>
        private Dest ReadReplyToHeader()
        {
           string replyToEncoding = ContentHeaderProperties.ReplyTo;

           if ( replyToEncoding == null )
           {
              return new Dest();
           } else
           {
              // Split the replyto field on a ':'
              string[] split = replyToEncoding.Split(':');

              // Ensure that the replyto field argument only consisted of two parts.
              if ( split.Length != 2 )
              {
                 throw new QpidException("Illegal value in ReplyTo property: " + replyToEncoding);
              }

              // Extract the exchange name and routing key from the split replyto field.
              string exchangeName = split[0];
              string routingKey = split[1];

              return new Dest(exchangeName, routingKey);
           }
        }

        private void WriteReplyToHeader(Dest dest)
        {
           string encodedDestination = string.Format("{0}:{1}", dest.ExchangeName, dest.RoutingKey);
           ContentHeaderProperties.ReplyTo = encodedDestination;
        }
    }
}
