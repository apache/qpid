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
using System.IO;
using System.Text;
using Qpid.Framing;
using Qpid.Messaging;
using Qpid.Buffer;

namespace Qpid.Client.Message
{
    public class QpidBytesMessage : AbstractQmsMessage, IBytesMessage
    {
        private const string MIME_TYPE = "application/octet-stream";

        private const int DEFAULT_BUFFER_INITIAL_SIZE = 1024;

        /// <summary>
        /// The backingstore for the data
        /// </summary>
        private MemoryStream _dataStream;

        private int _bodyLength;                

        private BinaryReader _reader;

        private BinaryWriter _writer;

        public QpidBytesMessage() : this(null)
        {            
        }

        /// <summary>
        /// Construct a bytes message with existing data.
        /// </summary>
        /// <param name="data">if data is not null, the message is immediately in read only mode. if data is null, it is in
        /// write-only mode</param>        
        QpidBytesMessage(ByteBuffer data) : base(data)
        {
            // superclass constructor has instantiated a content header at this point
            ContentHeaderProperties.ContentType = MIME_TYPE;
            if (data == null)
            {
                _data = ByteBuffer.Allocate(DEFAULT_BUFFER_INITIAL_SIZE);
                //_data.AutoExpand = true;
                _dataStream = new MemoryStream();
                _writer = new BinaryWriter(_dataStream);
            }
            else
            {
                _dataStream = new MemoryStream(data.ToByteArray());
                _bodyLength = data.ToByteArray().Length;
                _reader = new BinaryReader(_dataStream);
            }
        }

        internal QpidBytesMessage(long messageNbr, ContentHeaderBody contentHeader, ByteBuffer data)
            // TODO: this casting is ugly. Need to review whole ContentHeaderBody idea
            : base(messageNbr, (BasicContentHeaderProperties)contentHeader.Properties, data)
        {
            ContentHeaderProperties.ContentType = MIME_TYPE;
            _dataStream = new MemoryStream(data.ToByteArray());
            _bodyLength = data.ToByteArray().Length;
            _reader = new BinaryReader(_dataStream);
        
        }

        public override void ClearBodyImpl()
        {
            _data.Clear();
        }

//        public override void ClearBody()
//        {
//            if (_reader != null)
//            {
//                _reader.Close();
//                _reader = null;
//            }
//            _dataStream = new MemoryStream();
//            _bodyLength = 0;
//            
//            _writer = new BinaryWriter(_dataStream);
//        }

        public override string ToBodyString()
        {
            CheckReadable();
            try
            {
                return GetText();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString());
            }
        }
        
        private string GetText()
        {                  
            if (_dataStream != null)
            {
                // we cannot just read the underlying buffer since it may be larger than the amount of 
                // "filled" data. Length is not the same as Capacity.
                byte[] data = new byte[_dataStream.Length];
                _dataStream.Read(data, 0, (int)_dataStream.Length);
                return Encoding.UTF8.GetString(data);
            }
            else
            {
                return null;
            }
        }

        //public override byte[] Data
        //{
        //    get
        //    {
        //        if (_dataStream == null)
        //        {
        //            return null;
        //        }
        //        else
        //        {
        //            byte[] data = new byte[_dataStream.Length];
        //            _dataStream.Position = 0;
        //            _dataStream.Read(data, 0, (int) _dataStream.Length);
        //            return data;
        //        }
        //    }
        //    set
        //    {
        //        throw new NotSupportedException("Cannot set data payload except during construction");
        //    }
        //}

        public override string MimeType
        {
            get
            {
                return MIME_TYPE;
            }
        }

        public long BodyLength
        {
            get
            {
                CheckReadable();
                return _data.Limit; // XXX
//                return _bodyLength;
            }
        }

        /// <summary>
        ///  
        /// </summary>
        /// <exception cref="MessageNotReadableException">if the message is in write mode</exception>
//        private void CheckReadable() 
//        {
//
//            if (_reader == null)
//            {
//                throw new MessageNotReadableException("You need to call reset() to make the message readable");
//            }
//        }

        private void CheckWritable()
        {
            if (_reader != null)
            {
                throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
            }
        }

        public bool ReadBoolean()
        {
            CheckReadable();
            try
            {
                return _reader.ReadBoolean();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public byte ReadByte()
        {
            CheckReadable();
            try
            {
                return _reader.ReadByte();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public short ReadSignedByte()
        {
            CheckReadable();
            try
            {
                return _reader.ReadSByte();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public short ReadShort()
        {
            CheckReadable();
            try
            {
                return _reader.ReadInt16();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public char ReadChar()
        {
            CheckReadable();
            try
            {
                return _reader.ReadChar();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public int ReadInt()
        {
            CheckReadable();
            try
            {
                return _reader.ReadInt32();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public long ReadLong()
        {
            CheckReadable();
            try
            {
                return _reader.ReadInt64();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public float ReadFloat()
        {
            CheckReadable();
            try
            {
                return _reader.ReadSingle();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public double ReadDouble()
        {
            CheckReadable();
            try
            {
                return _reader.ReadDouble();
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public string ReadUTF()
        {
            CheckReadable();
            try
            {
                byte[] data = _reader.ReadBytes((int)_dataStream.Length);
                return Encoding.UTF8.GetString(data);                
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public int ReadBytes(byte[] bytes)
        {
            if (bytes == null)
            {
                throw new ArgumentNullException("bytes");
            }
            CheckReadable();
            try
            {                
                return _reader.Read(bytes, 0, bytes.Length);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public int ReadBytes(byte[] bytes, int count)
        {
            CheckReadable();
            if (bytes == null)
            {
                throw new ArgumentNullException("bytes");
            }
            if (count < 0)
            {
                throw new ArgumentOutOfRangeException("count must be >= 0");
            }
            if (count > bytes.Length)
            {
                count = bytes.Length;
            }

            try
            {
                return _reader.Read(bytes, 0, count);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteBoolean(bool b)
        {
            CheckWritable();
            try
            {
                _writer.Write(b);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteByte(byte b)
        {
            CheckWritable();
            try
            {
                _writer.Write(b);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteShort(short i)
        {
            CheckWritable();
            try
            {
                _writer.Write(i);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteChar(char c)
        {
            CheckWritable();
            try
            {
                _writer.Write(c);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteSignedByte(short value)
        {
            CheckWritable();
            try
            {
                _writer.Write(value);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteDouble(double value)
        {
            CheckWritable();
            try
            {
                _writer.Write(value);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteFloat(float value)
        {
            CheckWritable();
            try
            {
                _writer.Write(value);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteInt(int value)
        {
            CheckWritable();
            try
            {
                _writer.Write(value);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteLong(long value)
        {
            CheckWritable();
            try
            {
                _writer.Write(value);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }
        
        public void Write(int i)
        {
            CheckWritable();
            try
            {
                _writer.Write(i);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void Write(long l)
        {
            CheckWritable();
            try
            {
                _writer.Write(l);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void Write(float v)
        {
            CheckWritable();
            try
            {
                _writer.Write(v);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void Write(double v)
        {
            CheckWritable();
            try
            {
                _writer.Write(v);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteUTF(string value)
        {
            CheckWritable();
            try
            {
                byte[] encodedData = Encoding.UTF8.GetBytes(value);
                _writer.Write(encodedData);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteBytes(byte[] bytes)
        {
            CheckWritable();
            try
            {
                _writer.Write(bytes);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void WriteBytes(byte[] bytes, int offset, int length)
        {
            CheckWritable();
            try
            {
                _writer.Write(bytes, offset, length);
            }
            catch (IOException e)
            {
                throw new QpidException(e.ToString(), e);
            }
        }

        public void Reset()
        {
            base.Reset();
            _data.Flip();

//            CheckWritable();
//            try
//            {
//                _writer.Close();
//                _writer = null;
//                _reader = new BinaryReader(_dataStream);
//                _bodyLength = (int) _dataStream.Length;
//            }
//            catch (IOException e)
//            {
//                throw new QpidException(e.ToString(), e);
//            }
        }        
    }
}

