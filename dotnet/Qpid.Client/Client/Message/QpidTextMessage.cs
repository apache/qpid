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
using System.Text;
using Qpid.Framing;
using Qpid.Messaging;
using Qpid.Buffer;

namespace Qpid.Client.Message
{
    public class QpidTextMessage : AbstractQmsMessage, ITextMessage
    {
        private const string MIME_TYPE = "text/plain";

        private string _decodedValue;

        //public QpidTextMessage() : this(null, null)
        //{        
        //}

        //public QpidTextMessage(byte[] data, String encoding) : base()
        //{
        //    // the superclass has instantied a content header at this point
        //    ContentHeaderProperties.ContentType= MIME_TYPE;
        //    _data = data;
        //    ContentHeaderProperties.Encoding = encoding;
        //}

        //public QpidTextMessage(ulong messageNbr, byte[] data, BasicContentHeaderProperties contentHeader)
        //    : base(messageNbr, contentHeader)
        //{            
        //    contentHeader.ContentType = MIME_TYPE;
        //    _data = data;
        //}

        //public QpidTextMessage(byte[] data) : this(data, null)
        //{            
        //}

        //public QpidTextMessage(string text)
        //{
        //    Text = text;
        //}

        internal QpidTextMessage() : this(null, null)
        {
        }

        QpidTextMessage(ByteBuffer data, String encoding) : base(data)
        {
            ContentHeaderProperties.ContentType = MIME_TYPE;
            ContentHeaderProperties.Encoding = encoding;
        }

        internal QpidTextMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data)
            :base(deliveryTag, contentHeader, data)
        {
            contentHeader.ContentType = MIME_TYPE;
            _data = data;
        }

        QpidTextMessage(ByteBuffer data) : this(data, null)
        {
        }

        QpidTextMessage(String text) : base((ByteBuffer)null)
        {
            Text = text;
        }

        public override void ClearBodyImpl()
        {
            if (_data != null)
            {
                _data.release();
            }
            _data = null;
            _decodedValue = null;
        }

        public override string ToBodyString()
        {
            return Text;
        }

        public override string MimeType
        {
            get
            {
                return MIME_TYPE;
            }
        }        

        public string Text
        {
            get
            {
                if (_data == null && _decodedValue == null)
                {
                    return null;
                }
                else if (_decodedValue != null)
                {
                    return _decodedValue;
                }
                else
                {
                    // Read remaining bytes.
                    byte[] bytes = new byte[_data.remaining()];
                    _data.get(bytes);

                    // Convert to string based on encoding.
                    if (ContentHeaderProperties.Encoding != null)
                    {
                        // throw ArgumentException if the encoding is not supported
                        _decodedValue = Encoding.GetEncoding(ContentHeaderProperties.Encoding).GetString(bytes);
                    }
                    else
                    {
                        _decodedValue = Encoding.Default.GetString(bytes);
                    }
                    return _decodedValue;                    
                }
            }

            set
            {
                byte[] bytes;
                if (ContentHeaderProperties.Encoding == null)
                {
                    bytes = Encoding.Default.GetBytes(value);
                }
                else
                {
                    // throw ArgumentException if the encoding is not supported
                    bytes = Encoding.GetEncoding(ContentHeaderProperties.Encoding).GetBytes(value);
                }
                _data = ByteBuffer.wrap(bytes);
                _decodedValue = value;
            }
        }
    }
}
