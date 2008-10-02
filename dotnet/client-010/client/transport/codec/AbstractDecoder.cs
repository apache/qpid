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
using System.Collections.Generic;
using System.Diagnostics;
using System.Text;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport.codec
{
    /// <summary> 
    /// AbstractDecoder
    /// </summary>
    public abstract class AbstractDecoder : Decoder
    {
        private readonly Dictionary<Binary, String> str8cache = new Dictionary<Binary, String>();

        protected abstract byte doGet();

        protected abstract void doGet(byte[] bytes);
        public abstract bool hasRemaining();

        protected byte get()
        {
            return doGet();
        }

        protected void get(byte[] bytes)
        {
            doGet(bytes);
        }

        protected Binary get(int size)
        {
            byte[] bytes = new byte[size];
            get(bytes);
            return new Binary(bytes);
        }

        protected short uget()
        {
            return (short) (0xFF & get());
        }

        public virtual short readUint8()
        {
            return uget();
        }

        public abstract int readUint16();
       

        public abstract long readUint32();
      

        public int readSequenceNo()
        {
            return (int) readUint32();
        }

        public virtual long readUint64()
        {
            long l = 0;
            for (int i = 0; i < 8; i++)
            {
                l |= ((long) (0xFF & get())) << (56 - i*8);
            }
            return l;
        }

        public long readDatetime()
        {
            return readUint64();
        }

        private static String decode(byte[] bytes, int offset, int length, Encoding encoding)
        {
            return encoding.GetString(bytes, offset, length);
        }

        private static String decode(byte[] bytes, Encoding encoding)
        {
            return decode(bytes, 0, bytes.Length, encoding);
        }

        public String readStr8()
        {
            short size = readUint8();
            Binary bin = get(size);
            String str;
            if (! str8cache.TryGetValue(bin, out str))
            {
                str = decode(bin.array(), bin.offset(), bin.size(), Encoding.UTF8);
                str8cache.Add(bin, str);
            }
            return str;
        }

        public String readStr16()
        {
            int size = readUint16();
            byte[] bytes = new byte[size];
            get(bytes);
            return decode(bytes, Encoding.UTF8);
        }

        public byte[] readVbin8()
        {
            int size = readUint8();
            byte[] bytes = new byte[size];
            get(bytes);
            return bytes;
        }

        public byte[] readVbin16()
        {
            int size = readUint16();
            byte[] bytes = new byte[size];
            get(bytes);
            return bytes;
        }

        public byte[] readVbin32()
        {
            int size = (int) readUint32();
            byte[] bytes = new byte[size];
            get(bytes);
            return bytes;
        }

        public RangeSet readSequenceSet()
        {
            int count = readUint16()/8;
            if (count == 0)
            {
                return null;
            }
            RangeSet ranges = new RangeSet();
            for (int i = 0; i < count; i++)
            {
                ranges.add(readSequenceNo(), readSequenceNo());
            }
            return ranges;
        }

        public RangeSet readByteRanges()
        {
            throw new Exception("not implemented");
        }

        public UUID readUuid()
        {
            long msb = readUint64();
            long lsb = readUint64();
            return new UUID(msb, lsb);
        }

        public String readContent()
        {
            throw new Exception("Deprecated");
        }

        public Struct readStruct(int type)
        {
            Struct st = Struct.create(type);
            int width = st.getSizeWidth();
            if (width > 0)
            {
                long size = readSize(width);
                if (size == 0)
                {
                    return null;
                }
            }
            if (type > 0)
            {
                int code = readUint16();
                Debug.Assert(code == type);
            }
            st.read(this);
            return st;
        }

        public Struct readStruct32()
        {
            long size = readUint32();
            if (size == 0)
            {
                return null;
            }
            int type = readUint16();
            Struct result = Struct.create(type);
            result.read(this);
            return result;
        }

        public Dictionary<String, Object> readMap()
        {
            long size = readUint32();

            if (size == 0)
            {
                return null;
            }

            long count = readUint32();

            Dictionary<String, Object> result = new Dictionary<String, Object>();
            for (int i = 0; i < count; i++)
            {
                String key = readStr8();
                byte code = get();
                QpidType t = getType(code);
                Object value = read(t);
                result.Add(key, value);
            }

            return result;
        }

        public List<Object> readList()
        {
            long size = readUint32();

            if (size == 0)
            {
                return null;
            }

            long count = readUint32();

            List<Object> result = new List<Object>();
            for (int i = 0; i < count; i++)
            {
                byte code = get();
                QpidType t = getType(code);
                Object value = read(t);
                result.Add(value);
            }
            return result;
        }

        public List<Object> readArray()
        {
            long size = readUint32();

            if (size == 0)
            {
                return null;
            }

            byte code = get();
            QpidType t = getType(code);
            long count = readUint32();

            List<Object> result = new List<Object>();
            for (int i = 0; i < count; i++)
            {
                Object value = read(t);
                result.Add(value);
            }
            return result;
        }

        private QpidType getType(byte code)
        {
            return QpidType.get(code);
        }

        private long readSize(QpidType t)
        {
            return t.Fixed ? t.Width : readSize(t.Width);
        }

        private long readSize(int width)
        {
            switch (width)
            {
                case 1:
                    return readUint8();
                case 2:
                    return readUint16();
                case 4:
                    return readUint32();
                default:
                    throw new Exception("illegal width: " + width);
            }
        }

        private byte[] readBytes(QpidType t)
        {
            long size = readSize(t);
            byte[] result = new byte[(int) size];
            get(result);
            return result;
        }

        private Object read(QpidType t)
        {
            switch (t.Code)
            {
                case Code.BIN8:
                case Code.UINT8:
                    return readUint8();
                case Code.INT8:
                    return get();
                case Code.CHAR:
                    return (char) get();
                case Code.BOOLEAN:
                    return get() > 0;

                case Code.BIN16:
                case Code.UINT16:
                    return readUint16();
                case Code.INT16:
                    return (short) readUint16();

                case Code.BIN32:
                case Code.UINT32:
                    return readUint32();

                case Code.CHAR_UTF32:
                case Code.INT32:
                    return (int) readUint32();

                case Code.FLOAT:                    
                    return  (float)BitConverter.Int64BitsToDouble(readUint32() << 32);
                           
                case Code.BIN64:
                case Code.UINT64:
                case Code.INT64:
                case Code.DATETIME:
                    return readUint64();

                case Code.DOUBLE:                   
                    return BitConverter.Int64BitsToDouble(readUint64());
                case Code.UUID:
                    return readUuid();
                case Code.STR8:
                    return readStr8();
                case Code.STR16:
                    return readStr16();
                case Code.STR8_LATIN:
                case Code.STR8_UTF16:
                case Code.STR16_LATIN:
                case Code.STR16_UTF16:
                    // XXX: need to do character conversion
                    return Encoding.UTF8.GetString(readBytes(t));

                case Code.MAP:
                    return readMap();
                case Code.LIST:
                    return readList();
                case Code.ARRAY:
                    return readArray();
                case Code.STRUCT32:
                    return readStruct32();

                case Code.BIN40:
                case Code.DEC32:
                case Code.BIN72:
                case Code.DEC64:
                    // XXX: what types are we supposed to use here?
                    return readBytes(t);

                case Code.VOID:
                    return null;

                default:
                    return readBytes(t);
            }
        }
    }
}