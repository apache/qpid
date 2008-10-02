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
using System.IO;
using System.Text;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport.codec
{
    /// <summary> 
    /// AbstractEncoder
    /// </summary>
    public abstract class AbstractEncoder : Encoder
    {
        private static readonly Dictionary<Type, Code> ENCODINGS = new Dictionary<Type, Code>();
        private readonly Dictionary<String, byte[]> str8cache = new Dictionary<String, byte[]>();

        static AbstractEncoder()
        {
            ENCODINGS.Add(typeof (Boolean), Code.BOOLEAN);
            ENCODINGS.Add(typeof (String), Code.STR16);
            ENCODINGS.Add(typeof (long), Code.INT64);
            ENCODINGS.Add(typeof (int), Code.INT32);
            ENCODINGS.Add(typeof (short), Code.INT16);
            ENCODINGS.Add(typeof (Byte), Code.INT8);
            ENCODINGS.Add(typeof (Dictionary<String, Object>), Code.MAP);
            ENCODINGS.Add(typeof (List<Object>), Code.LIST);
            ENCODINGS.Add(typeof (float), Code.FLOAT);
            ENCODINGS.Add(typeof (Double), Code.DOUBLE);
            ENCODINGS.Add(typeof (char), Code.CHAR);
            ENCODINGS.Add(typeof (byte[]), Code.VBIN32);
        }

        protected abstract void doPut(byte b);

        protected abstract void doPut(MemoryStream src);


        protected void put(byte b)
        {
            doPut(b);
        }

        protected void put(MemoryStream src)
        {
            doPut(src);
        }

        protected virtual void put(byte[] bytes)
        {
            put(new MemoryStream(bytes));
        }

        protected abstract int beginSize8();
        protected abstract void endSize8(int pos);

        protected abstract int beginSize16();
        protected abstract void endSize16(int pos);

        protected abstract int beginSize32();
        protected abstract void endSize32(int pos);

        public virtual void writeUint8(short b)
        {
            Debug.Assert(b < 0x100);
            put((byte) b);
        }

        public virtual void writeUint16(int s)
        {
            Debug.Assert(s < 0x10000);
            put((byte) Functions.lsb(s >> 8));
            put((byte) Functions.lsb(s));
        }

        public virtual void writeUint32(long i)
        {
            Debug.Assert(i < 0x100000000L);
            put((byte) Functions.lsb(i >> 24));
            put((byte) Functions.lsb(i >> 16));
            put((byte) Functions.lsb(i >> 8));
            put((byte) Functions.lsb(i));
        }

        public void writeSequenceNo(int i)
        {
            writeUint32(i);
        }

        public virtual void writeUint64(long l)
        {
            for (int i = 0; i < 8; i++)
            {
                put((byte) Functions.lsb(l >> (56 - i*8)));
            }
        }


        public void writeDatetime(long l)
        {
            writeUint64(l);
        }

        private static byte[] encode(String s, Encoding encoding)
        {
            return encoding.GetBytes(s);
        }

        public void writeStr8(String s)
        {
            if (s == null)
            {
                s = "";
            }

            byte[] bytes;
            if (! str8cache.ContainsKey(s))
            {
                bytes = encode(s, Encoding.UTF8);
                str8cache.Add(s, bytes);
            }
            else
            {
                bytes = str8cache[s];
            }
            writeUint8((short) bytes.Length);
            put(bytes);
        }

        public void writeStr16(String s)
        {
            if (s == null)
            {
                s = "";
            }

            byte[] bytes = encode(s, Encoding.UTF8);
            writeUint16(bytes.Length);
            put(bytes);
        }

        public void writeVbin8(byte[] bytes)
        {
            if (bytes == null)
            {
                bytes = new byte[0];
            }
            if (bytes.Length > 255)
            {
                throw new Exception("array too long: " + bytes.Length);
            }
            writeUint8((short) bytes.Length);
            put(bytes);
        }

        public void writeVbin16(byte[] bytes)
        {
            if (bytes == null)
            {
                bytes = new byte[0];
            }
            writeUint16(bytes.Length);
            put(bytes);
        }

        public void writeVbin32(byte[] bytes)
        {
            if (bytes == null)
            {
                bytes = new byte[0];
            }
            writeUint32(bytes.Length);
            put(bytes);
        }

        public void writeSequenceSet(RangeSet ranges)
        {
            if (ranges == null)
            {
                writeUint16(0);
            }
            else
            {
                writeUint16(ranges.size()*8);
                foreach (Range range in ranges)
                {
                    writeSequenceNo(range.Lower);
                    writeSequenceNo(range.Upper);
                }
            }
        }

        public void writeByteRanges(RangeSet ranges)
        {
            throw new Exception("not implemented");
        }

        public void writeUuid(UUID uuid)
        {
            long msb = 0;
            long lsb = 0;
            if (uuid != null)
            {
                msb = uuid.MostSignificantBits;
                lsb = uuid.LeastSignificantBits;
            }
            writeUint64(msb);
            writeUint64(lsb);
        }

        public void writeStruct(int type, Struct s)
        {
            if (s == null)
            {
                s = Struct.create(type);
            }

            int width = s.getSizeWidth();
            int pos = -1;
            if (width > 0)
            {
                pos = beginSize(width);
            }

            if (type > 0)
            {
                writeUint16(type);
            }

            s.write(this);

            if (width > 0)
            {
                endSize(width, pos);
            }
        }

        public void writeStruct32(Struct s)
        {
            if (s == null)
            {
                writeUint32(0);
            }
            else
            {
                int pos = beginSize32();
                writeUint16(s.getEncodedType());
                s.write(this);
                endSize32(pos);
            }
        }

        private Code encoding(Object value)
        {
            if (value == null)
            {
                return Code.VOID;
            }

            Type klass = value.GetType();
            Code type = resolve(klass);

            if (type == Code.VOID)
            {
                throw new Exception
                    ("unable to resolve type: " + klass + ", " + value);
            }
            else
            {
                return type;
            }
        }

        private static Code resolve(Type klass)
        {
            Code type;
            if(ENCODINGS.ContainsKey(klass))
            {
                return ENCODINGS[klass];
            }
            
            Type sup = klass.BaseType;
            if (sup != null)
            {
                type = resolve(sup);

                if (type != Code.VOID)
                {
                    return type;
                }
            }
            foreach (Type iface in klass.GetInterfaces())
            {
                type = resolve(iface);
                if (type != Code.VOID)
                {
                    return type;
                }
            }
            return Code.VOID;
        }

        public void writeMap(Dictionary<String, Object> map)
        {
            int pos = beginSize32();
            if (map != null)
            {
                writeUint32(map.Count);
                writeMapEntries(map);
            }
            endSize32(pos);
        }

        protected void writeMapEntries(Dictionary<String, Object> map)
        {
            foreach (KeyValuePair<String, Object> entry in map)
            {
                String key = entry.Key;
                Object value = entry.Value;
                Code type = encoding(value);
                writeStr8(key);
                put((byte) type);
                write(type, value);
            }
        }

        public void writeList(List<Object> list)
        {
            int pos = beginSize32();
            if (list != null)
            {
                writeUint32(list.Count);
                writeListEntries(list);
            }
            endSize32(pos);
        }

        protected void writeListEntries(List<Object> list)
        {
            foreach (Object value in list)
            {
                Code type = encoding(value);
                put((byte) type);
                write(type, value);
            }
        }

        public void writeArray(List<Object> array)
        {
            int pos = beginSize32();
            if (array != null)
            {
                writeArrayEntries(array);
            }
            endSize32(pos);
        }

        protected void writeArrayEntries(List<Object> array)
        {
            Code type;

            if (array.Count == 0)
            {
                return;
            }
            else
            {
                type = encoding(array[0]);
            }
            put((byte) type);
            writeUint32(array.Count);

            foreach (Object value in array)
            {
                write(type, value);
            }
        }

        private void writeSize(QpidType t, int size)
        {
            if (t.Fixed)
            {
                if (size != t.width)
                {
                    throw new Exception("size does not match fixed width " + t.width + ": " + size);
                }
            }
            else
            {
                writeSize(t.width, size);
            }
        }

        private void writeSize(int width, int size)
        {
            // XXX: should check lengths
            switch (width)
            {
                case 1:
                    writeUint8((short) size);
                    break;
                case 2:
                    writeUint16(size);
                    break;
                case 4:
                    writeUint32(size);
                    break;
                default:
                    throw new Exception("illegal width: " + width);
            }
        }

        private int beginSize(int width)
        {
            switch (width)
            {
                case 1:
                    return beginSize8();
                case 2:
                    return beginSize16();
                case 4:
                    return beginSize32();
                default:
                    throw new Exception("illegal width: " + width);
            }
        }

        private void endSize(int width, int pos)
        {
            switch (width)
            {
                case 1:
                    endSize8(pos);
                    break;
                case 2:
                    endSize16(pos);
                    break;
                case 4:
                    endSize32(pos);
                    break;
                default:
                    throw new Exception("illegal width: " + width);
            }
        }

        private void writeBytes(QpidType t, byte[] bytes)
        {
            writeSize(t, bytes.Length);
            put(bytes);
        }

        private void write(Code t, Object value)
        {
            switch (t)
            {
                case Code.BIN8:
                case Code.UINT8:
                    writeUint8((short) value);
                    break;
                case Code.INT8:
                    put((Byte) value);
                    break;
                case Code.CHAR:
                    byte[] b = BitConverter.GetBytes((char) value);
                    put(b[0]);
                    break;
                case Code.BOOLEAN:
                    if ((bool) value)
                    {
                        put(1);
                    }
                    else
                    {
                        put(0);
                    }

                    break;

                case Code.BIN16:
                case Code.UINT16:
                    writeUint16((int) value);
                    break;

                case Code.INT16:
                    writeUint16((short) value);
                    break;

                case Code.BIN32:
                case Code.UINT32:
                    writeUint32((long) value);
                    break;

                case Code.CHAR_UTF32:
                case Code.INT32:
                    writeUint32((int) value);
                    break;

                case Code.FLOAT:
                    writeUint32(BitConverter.DoubleToInt64Bits((float) value) >> 32);
                    break;

                case Code.BIN64:
                case Code.UINT64:
                case Code.INT64:                   
                case Code.DATETIME:
                    writeUint64((long) value);
                    break;

                case Code.DOUBLE:
                    writeUint64( BitConverter.DoubleToInt64Bits((double) value));                    
                    break;

                case Code.UUID:
                    writeUuid((UUID) value);
                    break;

                case Code.STR8:
                    writeStr8((string) value);
                    break;

                case Code.STR16:
                    writeStr16((string) value);
                    break;

                case Code.STR8_LATIN:
                case Code.STR8_UTF16:
                case Code.STR16_LATIN:
                case Code.STR16_UTF16:
                    // XXX: need to do character conversion
                    writeBytes(QpidType.get((byte) t), encode((string) value, Encoding.Unicode));
                    break;

                case Code.MAP:
                    writeMap((Dictionary<String, Object>) value);
                    break;
                case Code.LIST:
                    writeList((List<Object>) value);
                    break;
                case Code.ARRAY:
                    writeList((List<Object>) value);
                    break;
                case Code.STRUCT32:
                    writeStruct32((Struct) value);
                    break;

                case Code.BIN40:
                case Code.DEC32:
                case Code.BIN72:
                case Code.DEC64:
                    // XXX: what types are we supposed to use here?
                    writeBytes(QpidType.get((byte) t), (byte[]) value);
                    break;

                case Code.VOID:
                    break;

                default:
                    writeBytes(QpidType.get((byte) t), (byte[]) value);
                    break;
            }
        }
    }
}