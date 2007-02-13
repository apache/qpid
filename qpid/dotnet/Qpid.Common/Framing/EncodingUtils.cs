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

      // SHORT STRING
      public static ushort EncodedShortStringLength(string s)
      {
         if ( s == null )
         {
            return 1;
         } else
         {
            return (ushort)(1 + s.Length);
         }
      }
      public static void WriteShortStringBytes(ByteBuffer buffer, string s)
      {
         if ( s != null )
         {
            //try
            //{
            //final byte[] encodedString = s.getBytes(STRING_ENCODING);
            byte[] encodedString;
            lock ( DEFAULT_ENCODER )
            {
               encodedString = DEFAULT_ENCODER.GetBytes(s);
            }
            // TODO: check length fits in an unsigned byte
            buffer.put((byte)encodedString.Length);
            buffer.put(encodedString);

         } else
         {
            // really writing out unsigned byte
            buffer.put((byte)0);
         }
      }

      // ASCII STRINGS
      public static uint EncodedAsciiStringLength(string s)
      {
         // TODO: move this to 2-byte length once the proposed encodings
         // have been approved. Also, validate length!
         if ( s == null )
            return 4;
         else 
            return (uint) (4 + s.Length);
      }
      public static string ReadAsciiString(ByteBuffer buffer)
      {
         return ReadLongString(buffer, DEFAULT_ENCODER);
      }
      public static void WriteAsciiString(ByteBuffer buffer, string s)
      {
         WriteLongStringBytes(buffer, s, DEFAULT_ENCODER);
      }

      // LONG STRING
      public static uint EncodedLongStringLength(string s)
      {
         return EncodedLongStringLength(s, DEFAULT_ENCODER);
      }

      public static uint EncodedLongStringLength(string s, Encoding encoding)
      {
         if ( s == null )
         {
            return 4;
         } else
         {
            return (uint)(4 + encoding.GetByteCount(s));
         }
      }
      public static string ReadLongString(ByteBuffer buffer)
      {
         return ReadLongString(buffer, DEFAULT_ENCODER);
      }
      public static string ReadLongString(ByteBuffer buffer, Encoding encoding)
      {
         uint length = buffer.getUnsignedInt();
         if ( length == 0 )
         {
            return null;
         } else
         {
            byte[] data = new byte[length];
            buffer.get(data);
            lock ( encoding )
            {
               return encoding.GetString(data);
            }
         }
      }
      public static void WriteLongStringBytes(ByteBuffer buffer, string s)
      {
         WriteLongStringBytes(buffer, s, DEFAULT_ENCODER);
      }

      public static void WriteLongStringBytes(ByteBuffer buffer, string s, Encoding encoding)
      {
         if ( !(s == null || s.Length <= 0xFFFE) )
         {
            throw new ArgumentException("String too long");
         }
         if ( s != null )
         {
            lock ( encoding )
            {
               byte[] encodedString = null;
               encodedString = encoding.GetBytes(s);
               buffer.put((uint)encodedString.Length);
               buffer.put(encodedString);
            }
         } else
         {
            buffer.put((uint)0);
         }
      }

      // BINARY
      public static uint EncodedLongstrLength(byte[] bytes)
      {
         if ( bytes == null )
         {
            return 4;
         } else
         {
            return (uint)(4 + bytes.Length);
         }
      }
      public static byte[] ReadLongstr(ByteBuffer buffer)
      {
         uint length = buffer.getUnsignedInt();
         if ( length == 0 )
         {
            return null;
         } else
         {
            byte[] result = new byte[length];
            buffer.get(result);
            return result;
         }
      }
      public static void WriteLongstr(ByteBuffer buffer, byte[] data)
      {
         if ( data != null )
         {
            buffer.put((uint)data.Length);
            buffer.put(data);
         } else
         {
            buffer.put((uint)0);
         }
      }

      // BOOLEANS
      public static bool[] ReadBooleans(ByteBuffer buffer)
      {
         byte packedValue = buffer.get();
         bool[] result = new bool[8];

         for ( int i = 0; i < 8; i++ )
         {
            result[i] = ((packedValue & (1 << i)) != 0);
         }
         return result;
      }
      public static void WriteBooleans(ByteBuffer buffer, bool[] values)
      {
         byte packedValue = 0;
         for ( int i = 0; i < values.Length; i++ )
         {
            if ( values[i] )
            {
               packedValue = (byte)(packedValue | (1 << i));
            }
         }

         buffer.put(packedValue);
      }

      // FIELD TABLES
      public static uint EncodedFieldTableLength(FieldTable table)
      {
         if ( table == null )
         {
            // size is encoded as 4 octets
            return 4;
         } else
         {
            // size of the table plus 4 octets for the size
            return table.EncodedSize + 4;
         }
      }
      /// <summary>
      /// Reads the field table using the data in the specified buffer
      /// </summary>
      /// <param name="buffer">The buffer to read from.</param>
      /// <returns>a populated field table</returns>
      /// <exception cref="AMQFrameDecodingException">if the buffer does not contain a decodable field table</exception>
      public static FieldTable ReadFieldTable(ByteBuffer buffer)
      {
         uint length = buffer.GetUnsignedInt();
         if ( length == 0 )
         {
            return null;
         } else
         {
            return new FieldTable(buffer, length);
         }
      }
      public static void WriteFieldTableBytes(ByteBuffer buffer, FieldTable table)
      {
         if ( table != null )
         {
            table.WriteToBuffer(buffer);
         } else
         {
            buffer.put((uint)0);
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
         if ( length == 0 )
         {
            return null;
         } else
         {
            byte[] data = new byte[length];
            buffer.get(data);

            lock ( DEFAULT_ENCODER )
            {
               return DEFAULT_ENCODER.GetString(data);
            }
         }
      }



      // BOOLEAN
      public static uint EncodedBooleanLength()
      {
         return 1;
      }
      public static bool ReadBoolean(ByteBuffer buffer)
      {
         byte packedValue = buffer.get();
         return (packedValue == 1);
      }
      public static void WriteBoolean(ByteBuffer buffer, bool value)
      {
         buffer.put((byte)(value ? 1 : 0));
      }


      // CHAR
      public static uint EncodedCharLength()
      {
         return EncodedByteLength();
      }
      public static char ReadChar(ByteBuffer buffer)
      {
         return (char)buffer.get();
      }
      public static void WriteChar(ByteBuffer buffer, char value)
      {
         buffer.put((byte)value);
      }

      // BYTE
      public static uint EncodedByteLength()
      {
         return 1;
      }
      public static byte ReadByte(ByteBuffer buffer)
      {
         return buffer.get();
      }
      public static void WriteByte(ByteBuffer buffer, byte value)
      {
         buffer.put(value);
      }

      // SBYTE
      public static uint EncodedSByteLength()
      {
         return 1;
      }
      public static sbyte ReadSByte(ByteBuffer buffer)
      {
         return (sbyte)buffer.get();
      }
      public static void WriteSByte(ByteBuffer buffer, sbyte value)
      {
         buffer.put((byte)value);
      }

      // INT16
      public static uint EncodedShortLength()
      {
         return 2;
      }

      public static short ReadShort(ByteBuffer buffer)
      {
         return buffer.getShort();
      }
      public static void WriteShort(ByteBuffer buffer, short value)
      {
         buffer.putShort(value);
      }

      // UINT16
      public static uint EncodedUnsignedShortLength()
      {
         return 2;
      }

      public static ushort ReadUnsignedShort(ByteBuffer buffer)
      {
         return buffer.GetUnsignedShort();
      }
      public static void WriteUnsignedShort(ByteBuffer buffer, ushort value)
      {
         buffer.put(value);
      }


      // INT32
      public static uint EncodedIntegerLength()
      {
         return 4;
      }
      public static int ReadInteger(ByteBuffer buffer)
      {
         return buffer.getInt();
      }
      public static void WriteInteger(ByteBuffer buffer, int value)
      {
         buffer.putInt(value);
      }

      // UINT32
      public static uint UnsignedIntegerLength()
      {
         return 4;
      }
      public static void WriteUnsignedInteger(ByteBuffer buffer, uint value)
      {
         buffer.put(value);
      }
      public static uint ReadUnsignedInteger(ByteBuffer buffer)
      {
         return buffer.getUnsignedInt();
      }

      // INT64
      public static uint EncodedUnsignedLongLength()
      {
         return 8;
      }
      public static ulong ReadUnsignedLong(ByteBuffer buffer)
      {
         return buffer.GetUnsignedLong();
      }
      public static void WriteUnsignedLong(ByteBuffer buffer, ulong value)
      {
         buffer.put(value);
      }
      
      // UINT64
      public static uint EncodedLongLength()
      {
         return 8;
      }
      public static long ReadLong(ByteBuffer buffer)
      {
         return buffer.getLong();
      }
      public static void WriteLong(ByteBuffer buffer, long value)
      {
         buffer.putLong(value);
      }

      // FLOAT
      public static uint EncodedFloatLength()
      {
         return 4;
      }
      public static void WriteFloat(ByteBuffer buffer, float value)
      {
         buffer.putFloat(value);
      }
      public static float ReadFloat(ByteBuffer buffer)
      {
         return buffer.getFloat();
      }

      // DOUBLE
      public static uint EncodedDoubleLength()
      {
         return 8;
      }
      public static void WriteDouble(ByteBuffer buffer, double value)
      {
         buffer.putDouble(value);
      }
      public static double ReadDouble(ByteBuffer buffer)
      {
         return buffer.getDouble();
      }

   }

}
