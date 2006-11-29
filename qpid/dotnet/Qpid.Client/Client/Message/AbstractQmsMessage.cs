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

//        protected long _messageNbr;

        protected bool _redelivered;

        protected ByteBuffer _data;

        //protected AbstractQmsMessage() : base(new BasicContentHeaderProperties())
        //{           
        //}

        //protected AbstractQmsMessage(ulong messageNbr, BasicContentHeaderProperties contentHeader)
        //    : this(contentHeader)
        //{            
        //    _messageNbr = messageNbr;
        //}

        //protected AbstractQmsMessage(BasicContentHeaderProperties contentHeader) 
        //    : base(contentHeader)
        //{            
        //}


#region new_java_ctrs

        protected AbstractQmsMessage(ByteBuffer data)
            : base(new BasicContentHeaderProperties())
        {
            _data = data;
            if (_data != null)
            {
                _data.Acquire();
            }
        }

        protected AbstractQmsMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data)
            : this(contentHeader, deliveryTag)
        {
            _data = data;
            if (_data != null)
            {
                _data.Acquire();
            }
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

        public abstract void ClearBody();

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
                // make sure we rewind the data just in case any method has moved the
                // position beyond the start
                if (_data != null)
                {
                    _data.Rewind();
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

        /// <summary>
        /// Get the AMQ message number assigned to this message
        /// </summary>
        /// <returns>the message number</returns>
        //public ulong MessageNbr
        //{
        //    get
        //    {
        //        return _messageNbr;
        //    }
        //    set
        //    {
        //        _messageNbr = value;
        //    }
        //}        

        public BasicContentHeaderProperties ContentHeaderProperties
        {
            get
            {
                return (BasicContentHeaderProperties) _contentHeaderProperties;
            }
        }
    }

    internal class QpidHeaders : IHeaders
    {
        public const char BOOLEAN_PROPERTY_PREFIX = 'B';
        public const char BYTE_PROPERTY_PREFIX = 'b';
        public const char SHORT_PROPERTY_PREFIX = 's';
        public const char INT_PROPERTY_PREFIX = 'i';
        public const char LONG_PROPERTY_PREFIX = 'l';
        public const char FLOAT_PROPERTY_PREFIX = 'f';
        public const char DOUBLE_PROPERTY_PREFIX = 'd';
        public const char STRING_PROPERTY_PREFIX = 'S';

        AbstractQmsMessage _message;
        
        public QpidHeaders(AbstractQmsMessage message)
        {
            _message = message;
        }

        public bool Contains(string name)
        {
            CheckPropertyName(name);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return false;
            }
            else
            {
                // TODO: fix this
                return _message.ContentHeaderProperties.Headers.Contains(STRING_PROPERTY_PREFIX + name);
            }
        }

        public void Clear()
        {
            if (_message.ContentHeaderProperties.Headers != null)
            {
                _message.ContentHeaderProperties.Headers.Clear();
            }
        }

        public string this[string name]
        {
            get 
            { 
                return GetString(name);
            }
            set
            {
                SetString(name, value);
            }
        }

        public bool GetBoolean(string name)
        {
            CheckPropertyName(name);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return false;
            }
            else
            {
                object b = _message.ContentHeaderProperties.Headers[BOOLEAN_PROPERTY_PREFIX + name];

                if (b == null)
                {
                    return false;
                }
                else
                {
                    return (bool)b;
                }
            }
        }

        public void SetBoolean(string name, bool b)
        {
            CheckPropertyName(name);
            _message.ContentHeaderProperties.Headers[BOOLEAN_PROPERTY_PREFIX + name] = b;
        }

        public byte GetByte(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object b = _message.ContentHeaderProperties.Headers[BYTE_PROPERTY_PREFIX + propertyName];
                if (b == null)
                {
                    return 0;
                }
                else
                {
                    return (byte)b;
                }
            }
        }

        public void SetByte(string propertyName, byte b)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[BYTE_PROPERTY_PREFIX + propertyName] = b;
        }

        public short GetShort(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object s = _message.ContentHeaderProperties.Headers[SHORT_PROPERTY_PREFIX + propertyName];
                if (s == null)
                {
                    return 0;
                }
                else
                {
                    return (short)s;
                }
            }
        }

        public void SetShort(string propertyName, short i)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[SHORT_PROPERTY_PREFIX + propertyName] = i;
        }

        public int GetInt(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object i = _message.ContentHeaderProperties.Headers[INT_PROPERTY_PREFIX + propertyName];
                if (i == null)
                {
                    return 0;
                }
                else
                {
                    return (int)i;
                }
            }
        }

        public void SetInt(string propertyName, int i)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[INT_PROPERTY_PREFIX + propertyName] = i;
        }

        public long GetLong(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object l = _message.ContentHeaderProperties.Headers[LONG_PROPERTY_PREFIX + propertyName];
                if (l == null)
                {
                    // temp - the spec says do this but this throws a NumberFormatException
                    //return Long.valueOf(null).longValue();
                    return 0;
                }
                else
                {
                    return (long)l;
                }
            }
        }

        public void SetLong(string propertyName, long l)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[LONG_PROPERTY_PREFIX + propertyName] = l;
        }

        public float GetFloat(String propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object f = _message.ContentHeaderProperties.Headers[FLOAT_PROPERTY_PREFIX + propertyName];
                if (f == null)
                {
                    return 0;
                }
                else
                {
                    return (float)f;
                }
            }
        }

        public void SetFloat(string propertyName, float f)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[FLOAT_PROPERTY_PREFIX + propertyName] = f;
        }

        public double GetDouble(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return 0;
            }
            else
            {
                object d = _message.ContentHeaderProperties.Headers[DOUBLE_PROPERTY_PREFIX + propertyName];
                if (d == null)
                {
                    return 0;
                }
                else
                {
                    return (double)d;
                }
            }
        }

        public void SetDouble(string propertyName, double v)
        {
            CheckPropertyName(propertyName);
            _message.ContentHeaderProperties.Headers[DOUBLE_PROPERTY_PREFIX + propertyName] = v;
        }

        public string GetString(string propertyName)
        {
            CheckPropertyName(propertyName);
            if (_message.ContentHeaderProperties.Headers == null)
            {
                return null;
            }
            else
            {
                return (string)_message.ContentHeaderProperties.Headers[STRING_PROPERTY_PREFIX + propertyName];
            }
        }

        public void SetString(string propertyName, string value)
        {
            CheckPropertyName(propertyName);
            CreatePropertyMapIfRequired();
            propertyName = STRING_PROPERTY_PREFIX + propertyName;
            _message.ContentHeaderProperties.Headers[propertyName] = value;
        }

        private void CheckPropertyName(string propertyName)
        {
            if (propertyName == null)
            {
                throw new ArgumentException("Property name must not be null");
            }
            else if ("".Equals(propertyName))
            {
                throw new ArgumentException("Property name must not be the empty string");
            }

            if (_message.ContentHeaderProperties.Headers == null)
            {
                _message.ContentHeaderProperties.Headers = new FieldTable();
            }
        }

        private void CreatePropertyMapIfRequired()
        {
            if (_message.ContentHeaderProperties.Headers == null)
            {
                _message.ContentHeaderProperties.Headers = new FieldTable();
            }
        }

        public override string ToString()
        {
            StringBuilder buf = new StringBuilder("{");
            int i = 0;
            foreach (DictionaryEntry entry in _message.ContentHeaderProperties.Headers)
            {
                ++i;
                if (i > 1)
                {
                    buf.Append(", ");
                }
                string propertyName = (string)entry.Key;
                if (propertyName == null)
                {
                    buf.Append("\nInternal error: Property with NULL key defined");
                }
                else
                {
                    buf.Append(propertyName.Substring(1));

                    buf.Append(" : ");

                    char typeIdentifier = propertyName[0];
                    buf.Append(typeIdentifierToName(typeIdentifier));
                    buf.Append(" = ").Append(entry.Value);
                }
            }
            buf.Append("}");
            return buf.ToString();
        }

        private static string typeIdentifierToName(char typeIdentifier)
        {
            switch (typeIdentifier)
            {
                case BOOLEAN_PROPERTY_PREFIX:
                    return "boolean";
                case BYTE_PROPERTY_PREFIX:
                    return "byte";
                case SHORT_PROPERTY_PREFIX:
                    return "short";
                case INT_PROPERTY_PREFIX:
                    return "int";
                case LONG_PROPERTY_PREFIX:
                    return "long";
                case FLOAT_PROPERTY_PREFIX:
                    return "float";
                case DOUBLE_PROPERTY_PREFIX:
                    return "double";
                case STRING_PROPERTY_PREFIX:
                    return "string";
                default:
                    return "unknown ( '" + typeIdentifier + "')";
            }
        }
    }
}
