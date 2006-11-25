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

namespace Qpid.Client.Message
{
    public class QpidTextMessage : AbstractQmsMessage, ITextMessage
    {
        private const string MIME_TYPE = "text/plain";

        private byte[] _data;

        private string _decodedValue;

        public QpidTextMessage() : this(null, null)
        {        
        }

        public QpidTextMessage(byte[] data, String encoding) : base()
        {
            // the superclass has instantied a content header at this point
            ContentHeaderProperties.ContentType= MIME_TYPE;
            _data = data;
            ContentHeaderProperties.Encoding = encoding;
        }

        public QpidTextMessage(ulong messageNbr, byte[] data, BasicContentHeaderProperties contentHeader)
            : base(messageNbr, contentHeader)
        {            
            contentHeader.ContentType = MIME_TYPE;
            _data = data;
        }

        public QpidTextMessage(byte[] data) : this(data, null)
        {            
        }

        public QpidTextMessage(string text)
        {
            Text = text;
        }

        public override void ClearBody()
        {
            _data = null;
            _decodedValue = null;
        }

        public override string ToBodyString()
        {
            return Text;
        }

        public override byte[] Data
        {
            get
            {
                return _data;
            }
            set
            {
                _data = value;
            }
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
                    if (ContentHeaderProperties.Encoding != null)
                    {
                        // throw ArgumentException if the encoding is not supported
                        _decodedValue = Encoding.GetEncoding(ContentHeaderProperties.Encoding).GetString(_data);
                    }
                    else
                    {
                        _decodedValue = Encoding.Default.GetString(_data);
                    }
                    return _decodedValue;                    
                }
            }

            set
            {            
                if (ContentHeaderProperties.Encoding == null)
                {
                    _data = Encoding.Default.GetBytes(value);
                }
                else
                {
                    // throw ArgumentException if the encoding is not supported
                    _data = Encoding.GetEncoding(ContentHeaderProperties.Encoding).GetBytes(value);
                }
                _decodedValue = value;
            }
        }
    }
}
