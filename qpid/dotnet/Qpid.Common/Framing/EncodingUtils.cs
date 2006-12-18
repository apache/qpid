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
using Qpid.Buffer;

namespace Qpid.Framing
{
    public class EncodingUtils
    {
        private static readonly Encoding DEFAULT_ENCODER = Encoding.ASCII;
        
        public static ushort EncodedShortStringLength(string s)
        {
            if (s == null)
            {
                return 1;
            }
            else
            {
                return (ushort)(1 + s.Length);
            }
        }

        public static uint EncodedLongStringLength(string s)
        {
            if (s == null)
            {
                return 4;
            }
            else
            {
                return (uint)(4 + s.Length);
            }
        }

        public static int EncodedLongstrLength(byte[] bytes)
        {
            if (bytes == null)
            {
                return 4;
            }
            else
            {
                return 4 + bytes.Length;
            }
        }

        public static uint EncodedFieldTableLength(FieldTable table)
        {
            if (table == null)
            {
                // size is encoded as 4 octets
                return 4;
            }
            else
            {
                // size of the table plus 4 octets for the size
                return table.EncodedSize + 4;
            }
        }

        public static void WriteShortStringBytes(ByteBuffer buffer, string s)
        {
            if (s != null)
            {
                //try
                //{
                    //final byte[] encodedString = s.getBytes(STRING_ENCODING);
                byte[] encodedString;
                lock (DEFAULT_ENCODER)
                {
                    encodedString = DEFAULT_ENCODER.GetBytes(s);
                }
                // TODO: check length fits in an unsigned byte
                buffer.put((byte) encodedString.Length);
                buffer.put(encodedString);
                
            }
            else
            {
                // really writing out unsigned byte
                buffer.put((byte) 0);
            }
        }

        public static void WriteLongStringBytes(ByteBuffer buffer, string s)
        {
            if (!(s == null || s.Length <= 0xFFFE))
            {
                throw new ArgumentException("String too long");
            }
            if (s != null)
            {
                buffer.put((uint)s.Length);
                byte[] encodedString = null;
                lock (DEFAULT_ENCODER)
                {
                    encodedString = DEFAULT_ENCODER.GetBytes(s);
                }
                buffer.put(encodedString);
            }            
            else
            {
                buffer.put((uint) 0);
            }
        }

        public static void WriteFieldTableBytes(ByteBuffer buffer, FieldTable table)
        {
            if (table != null)
            {
                table.WriteToBuffer(buffer);
            }
            else
            {
                buffer.put((uint) 0);
            }
        }

        public static void WriteBooleans(ByteBuffer buffer, bool[] values)
        {
            byte packedValue = 0;
            for (int i = 0; i < values.Length; i++)
            {
                if (values[i])
                {
                    packedValue = (byte) (packedValue | (1 << i));
                }
            }

            buffer.put(packedValue);
        }

        public static void WriteLongstr(ByteBuffer buffer, byte[] data)
        {
            if (data != null)
            {
                buffer.put((uint) data.Length);
                buffer.put(data);
            }
            else
            {
                buffer.put((uint) 0);
            }
        }

        public static bool[] ReadBooleans(ByteBuffer buffer)
        {
            byte packedValue = buffer.get();
            bool[] result = new bool[8];

            for (int i = 0; i < 8; i++)
            {
                result[i] = ((packedValue & (1 << i)) != 0);
            }
            return result;
        }

        /// <summary>
        /// Reads the field table uaing the data in the specified buffer
        /// </summary>
        /// <param name="buffer">The buffer to read from.</param>
        /// <returns>a populated field table</returns>
        /// <exception cref="AMQFrameDecodingException">if the buffer does not contain a decodable field table</exception>
        public static FieldTable ReadFieldTable(ByteBuffer buffer)
        {
            uint length = buffer.GetUnsignedInt();
            if (length == 0)
            {
                return null;
            }
            else
            {
                return new FieldTable(buffer, length);
            }
        }

        /// <summary>
        /// Read a short string from the buffer
        /// </summary>
        /// <param name="buffer">The buffer to read from.</param>
        /// <returns>a string</returns>
        /// <exception cref="AMQFrameDecodingException">if the buffer does not contain a decodable short string</exception>
        public static string ReadShortString(ByteBuffer buffer) 
        {
            byte length = buffer.get();
            if (length == 0)
            {
                return null;
            }
            else
            {
                byte[] data = new byte[length];
                buffer.get(data);

                lock (DEFAULT_ENCODER)
                {
                    return DEFAULT_ENCODER.GetString(data);
//                    return buffer.GetString(length, DEFAULT_ENCODER);                    
                }                
            }
        }

        public static string ReadLongString(ByteBuffer buffer)
        {
            uint length = buffer.getUnsignedInt();
            if (length == 0)
            {
                return null;
            }
            else
            {
                byte[] data = new byte[length];
                buffer.get(data);
                lock (DEFAULT_ENCODER)
                {
                    return DEFAULT_ENCODER.GetString(data);
                    //return buffer.GetString(length, DEFAULT_ENCODER);
                }                
            }
        }

        public static byte[] ReadLongstr(ByteBuffer buffer)
        {
            uint length = buffer.getUnsignedInt();
            if (length == 0)
            {
                return null;
            }
            else
            {
                byte[] result = new byte[length];
                buffer.get(result);
                return result;
            }
        }
    }

}
