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
package org.apache.qpid.framing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class EncodingUtils
{
    private static final Logger _logger = LoggerFactory.getLogger(EncodingUtils.class);

    private static final String STRING_ENCODING = "iso8859-15";

    private static final Charset _charset = Charset.forName("iso8859-15");

    public static final int SIZEOF_UNSIGNED_SHORT = 2;
    public static final int SIZEOF_UNSIGNED_INT = 4;
    private static final boolean[] ALL_FALSE_ARRAY = new boolean[8];

    private EncodingUtils()
    {
    }

    public static int encodedShortStringLength(String s)
    {
        if (s == null)
        {
            return 1;
        }
        else
        {
            return (short) (1 + s.length());
        }
    }

    public static int encodedShortStringLength(short s)
    {
        if (s == 0)
        {
            return 1 + 1;
        }

        int len = 0;
        if (s < 0)
        {
            len = 1;
            // sloppy - doesn't work of Integer.MIN_VALUE
            s = (short) -s;
        }

        if (s > 9999)
        {
            return 1 + 5;
        }
        else if (s > 999)
        {
            return 1 + 4;
        }
        else if (s > 99)
        {
            return 1 + 3;
        }
        else if (s > 9)
        {
            return 1 + 2;
        }
        else
        {
            return 1 + 1;
        }

    }

    public static int encodedShortStringLength(int i)
    {
        if (i == 0)
        {
            return 1 + 1;
        }

        int len = 0;
        if (i < 0)
        {
            len = 1;
            // sloppy - doesn't work of Integer.MIN_VALUE
            i = -i;
        }

        // range is now 1 - 2147483647
        if (i < Short.MAX_VALUE)
        {
            return len + encodedShortStringLength((short) i);
        }
        else if (i > 999999)
        {
            return len + 6 + encodedShortStringLength((short) (i / 1000000));
        }
        else // if i > 99999
        {
            return len + 5 + encodedShortStringLength((short) (i / 100000));
        }

    }

    public static int encodedShortStringLength(long l)
    {
        if (l == 0)
        {
            return 1 + 1;
        }

        int len = 0;
        if (l < 0)
        {
            len = 1;
            // sloppy - doesn't work of Long.MIN_VALUE
            l = -l;
        }

        if (l < Integer.MAX_VALUE)
        {
            return len + encodedShortStringLength((int) l);
        }
        else if (l > 9999999999L)
        {
            return len + 10 + encodedShortStringLength((int) (l / 10000000000L));
        }
        else
        {
            return len + 1 + encodedShortStringLength((int) (l / 10L));
        }

    }

    public static int encodedShortStringLength(AMQShortString s)
    {
        if (s == null)
        {
            return 1;
        }
        else
        {
            return (1 + s.length());
        }
    }

    public static int encodedLongStringLength(String s)
    {
        if (s == null)
        {
            return 4;
        }
        else
        {
            return 4 + s.length();
        }
    }

    public static int encodedLongStringLength(char[] s)
    {
        if (s == null)
        {
            return 4;
        }
        else
        {
            return 4 + s.length;
        }
    }

    public static int encodedLongstrLength(byte[] bytes)
    {
        if (bytes == null)
        {
            return 4;
        }
        else
        {
            return 4 + bytes.length;
        }
    }

    public static int encodedFieldTableLength(FieldTable table)
    {
        if (table == null)
        {
            // length is encoded as 4 octets
            return 4;
        }
        else
        {
            // length of the table plus 4 octets for the length
            return (int) table.getEncodedSize() + 4;
        }
    }

    public static int encodedContentLength(Content table)
    {
        // TODO: New Content class required for AMQP 0-9.
        return 0;
    }

    public static void writeShortStringBytes(DataOutput buffer, String s) throws IOException
    {
        if (s != null)
        {
            byte[] encodedString = new byte[s.length()];
            char[] cha = s.toCharArray();
            for (int i = 0; i < cha.length; i++)
            {
                encodedString[i] = (byte) cha[i];
            }

            // TODO: check length fits in an unsigned byte
            writeUnsignedByte(buffer,  (short)encodedString.length);
            buffer.write(encodedString);


        }
        else
        {
            // really writing out unsigned byte
            buffer.write((byte) 0);
        }
    }

    public static void writeShortStringBytes(DataOutput buffer, AMQShortString s) throws IOException
    {
        if (s != null)
        {

            s.writeToBuffer(buffer);
        }
        else
        {
            // really writing out unsigned byte
            buffer.write((byte) 0);
        }
    }

    public static void writeLongStringBytes(DataOutput buffer, String s) throws IOException
    {
        if (s != null)
        {
            int len = s.length();
            writeUnsignedInteger(buffer, s.length());
            byte[] encodedString = new byte[len];
            char[] cha = s.toCharArray();
            for (int i = 0; i < cha.length; i++)
            {
                encodedString[i] = (byte) cha[i];
            }

            buffer.write(encodedString);
        }
        else
        {
            writeUnsignedInteger(buffer, 0);
        }
    }

    public static void writeLongStringBytes(DataOutput buffer, char[] s) throws IOException
    {
        if (s != null)
        {
            int len = s.length;
            writeUnsignedInteger(buffer, s.length);
            byte[] encodedString = new byte[len];
            for (int i = 0; i < s.length; i++)
            {
                encodedString[i] = (byte) s[i];
            }

            buffer.write(encodedString);
        }
        else
        {
            writeUnsignedInteger(buffer, 0);
        }
    }

    public static void writeLongStringBytes(DataOutput buffer, byte[] bytes) throws IOException
    {
        if (bytes != null)
        {
            writeUnsignedInteger(buffer, bytes.length);
            buffer.write(bytes);
        }
        else
        {
            writeUnsignedInteger(buffer, 0);
        }
    }

    public static void writeUnsignedByte(DataOutput buffer, short b) throws IOException
    {
        byte bv = (byte) b;
        buffer.write(bv);
    }

    public static void writeUnsignedShort(DataOutput buffer, int s) throws IOException
    {
        // TODO: Is this comparison safe? Do I need to cast RHS to long?
        if (s < Short.MAX_VALUE)
        {
            buffer.writeShort(s);
        }
        else
        {
            short sv = (short) s;
            buffer.write((byte) (0xFF & (sv >> 8)));
            buffer.write((byte) (0xFF & sv));
        }
    }

    public static int unsignedIntegerLength()
    {
        return 4;
    }

    public static void writeUnsignedInteger(DataOutput buffer, long l) throws IOException
    {
        // TODO: Is this comparison safe? Do I need to cast RHS to long?
        if (l < Integer.MAX_VALUE)
        {
            buffer.writeInt((int) l);
        }
        else
        {
            int iv = (int) l;

            // FIXME: This *may* go faster if we build this into a local 4-byte array and then
            // put the array in a single call.
            buffer.write((byte) (0xFF & (iv >> 24)));
            buffer.write((byte) (0xFF & (iv >> 16)));
            buffer.write((byte) (0xFF & (iv >> 8)));
            buffer.write((byte) (0xFF & iv));
        }
    }

    public static void writeFieldTableBytes(DataOutput buffer, FieldTable table) throws IOException
    {
        if (table != null)
        {
            table.writeToBuffer(buffer);
        }
        else
        {
            EncodingUtils.writeUnsignedInteger(buffer, 0);
        }
    }

    public static void writeContentBytes(DataOutput buffer, Content content)
    {
        // TODO: New Content class required for AMQP 0-9.
    }

    public static void writeBooleans(DataOutput buffer, boolean[] values) throws IOException
    {
        byte packedValue = 0;
        for (int i = 0; i < values.length; i++)
        {
            if (values[i])
            {
                packedValue = (byte) (packedValue | (1 << i));
            }
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value) throws IOException
    {

        buffer.write(value ? (byte) 1 : (byte) 0);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1, boolean value2) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        if (value2)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 2));
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1, boolean value2, boolean value3) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        if (value2)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 2));
        }

        if (value3)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 3));
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1, boolean value2, boolean value3,
        boolean value4) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        if (value2)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 2));
        }

        if (value3)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 3));
        }

        if (value4)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 4));
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1, boolean value2, boolean value3,
        boolean value4, boolean value5) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        if (value2)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 2));
        }

        if (value3)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 3));
        }

        if (value4)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 4));
        }

        if (value5)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 5));
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1, boolean value2, boolean value3,
        boolean value4, boolean value5, boolean value6) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        if (value2)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 2));
        }

        if (value3)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 3));
        }

        if (value4)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 4));
        }

        if (value5)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 5));
        }

        if (value6)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 6));
        }

        buffer.write(packedValue);
    }

    public static void writeBooleans(DataOutput buffer, boolean value0, boolean value1, boolean value2, boolean value3,
        boolean value4, boolean value5, boolean value6, boolean value7) throws IOException
    {
        byte packedValue = value0 ? (byte) 1 : (byte) 0;

        if (value1)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 1));
        }

        if (value2)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 2));
        }

        if (value3)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 3));
        }

        if (value4)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 4));
        }

        if (value5)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 5));
        }

        if (value6)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 6));
        }

        if (value7)
        {
            packedValue = (byte) (packedValue | (byte) (1 << 7));
        }

        buffer.write(packedValue);
    }

    /**
     * This is used for writing longstrs.
     * @param buffer buffer to write to
     * @param data data to write
     * @throws IOException if there is an issue writing the output
     */
    public static void writeLongstr(DataOutput buffer, byte[] data) throws IOException
    {
        if (data != null)
        {
            writeUnsignedInteger(buffer, data.length);
            buffer.write(data);
        }
        else
        {
            writeUnsignedInteger(buffer, 0);
        }
    }

    public static void writeTimestamp(DataOutput buffer, long timestamp) throws IOException
    {
        writeLong(buffer, timestamp);
    }

    public static boolean[] readBooleans(DataInput buffer) throws IOException
    {
        final byte packedValue = buffer.readByte();
        if (packedValue == 0)
        {
            return ALL_FALSE_ARRAY;
        }

        final boolean[] result = new boolean[8];

        result[0] = ((packedValue & 1) != 0);
        result[1] = ((packedValue & (1 << 1)) != 0);
        result[2] = ((packedValue & (1 << 2)) != 0);
        result[3] = ((packedValue & (1 << 3)) != 0);
        if ((packedValue & 0xF0) == 0)
        {
            result[0] = ((packedValue & 1) != 0);
        }

        result[4] = ((packedValue & (1 << 4)) != 0);
        result[5] = ((packedValue & (1 << 5)) != 0);
        result[6] = ((packedValue & (1 << 6)) != 0);
        result[7] = ((packedValue & (1 << 7)) != 0);

        return result;
    }

    public static FieldTable readFieldTable(DataInput buffer) throws AMQFrameDecodingException, IOException
    {
        long length = ((long)(buffer.readInt())) & 0xFFFFFFFFL;
        if (length == 0)
        {
            return null;
        }
        else
        {
            return FieldTableFactory.newFieldTable(buffer, length);
        }
    }

    public static Content readContent(DataInput buffer) throws AMQFrameDecodingException
    {
        // TODO: New Content class required for AMQP 0-9.
        return null;
    }

    public static AMQShortString readAMQShortString(DataInput buffer) throws IOException
    {
        return AMQShortString.readFromBuffer(buffer);

    }

    public static String readShortString(DataInput buffer) throws IOException
    {
        short length = (short) (((short)buffer.readByte()) & 0xFF);
        if (length == 0)
        {
            return null;
        }
        else
        {
            // this may seem rather odd to declare two array but testing has shown
            // that constructing a string from a byte array is 5 (five) times slower
            // than constructing one from a char array.
            // this approach here is valid since we know that all the chars are
            // ASCII (0-127)
            byte[] stringBytes = new byte[length];
            buffer.readFully(stringBytes, 0, length);
            char[] stringChars = new char[length];
            for (int i = 0; i < stringChars.length; i++)
            {
                stringChars[i] = (char) stringBytes[i];
            }

            return new String(stringChars);
        }
    }

    public static String readLongString(DataInput buffer) throws IOException
    {
        long length = ((long)(buffer.readInt())) & 0xFFFFFFFFL;
        if (length == 0)
        {
            return "";
        }
        else
        {
            // this may seem rather odd to declare two array but testing has shown
            // that constructing a string from a byte array is 5 (five) times slower
            // than constructing one from a char array.
            // this approach here is valid since we know that all the chars are
            // ASCII (0-127)
            byte[] stringBytes = new byte[(int) length];
            buffer.readFully(stringBytes, 0, (int) length);
            char[] stringChars = new char[(int) length];
            for (int i = 0; i < stringChars.length; i++)
            {
                stringChars[i] = (char) stringBytes[i];
            }

            return new String(stringChars);
        }
    }

    public static byte[] readLongstr(DataInput buffer) throws IOException
    {
        long length = ((long)(buffer.readInt())) & 0xFFFFFFFFL;
        if (length == 0)
        {
            return null;
        }
        else
        {
            byte[] result = new byte[(int) length];
            buffer.readFully(result);

            return result;
        }
    }

    public static long readTimestamp(DataInput buffer) throws IOException
    {
        return buffer.readLong();
    }

    static byte[] hexToByteArray(String id)
    {
        // Should check param for null, long enough for this check, upper-case and trailing char
        String s = (id.charAt(1) == 'x') ? id.substring(2) : id; // strip 0x

        int len = s.length();
        int byte_len = len / 2;
        byte[] b = new byte[byte_len];

        for (int i = 0; i < byte_len; i++)
        {
            // fixme: refine these repetitive subscript calcs.
            int ch = i * 2;

            byte b1 = Byte.parseByte(s.substring(ch, ch + 1), 16);
            byte b2 = Byte.parseByte(s.substring(ch + 1, ch + 2), 16);

            b[i] = (byte) ((b1 * 16) + b2);
        }

        return (b);
    }

    public static char[] convertToHexCharArray(byte[] from)
    {
        int length = from.length;
        char[] result_buff = new char[(length * 2) + 2];

        result_buff[0] = '0';
        result_buff[1] = 'x';

        int bite;
        int dest = 2;

        for (int i = 0; i < length; i++)
        {
            bite = from[i];

            if (bite < 0)
            {
                bite += 256;
            }

            result_buff[dest++] = hex_chars[bite >> 4];
            result_buff[dest++] = hex_chars[bite & 0x0f];
        }

        return (result_buff);
    }

    public static String convertToHexString(byte[] from)
    {
        return (new String(convertToHexCharArray(from)));
    }

    public static String convertToHexString(ByteBuffer bb)
    {
        int size = bb.limit();

        byte[] from = new byte[size];

        for (int i = 0; i < size; i++)
        {
            from[i] = bb.get(i);
        }

        return (new String(convertToHexCharArray(from)));
    }

    private static char[] hex_chars = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    // **** new methods

    // AMQP_BOOLEAN_PROPERTY_PREFIX

    public static void writeBoolean(DataOutput buffer, boolean aBoolean) throws IOException
    {
        buffer.write(aBoolean ? 1 : 0);
    }

    public static boolean readBoolean(DataInput buffer) throws IOException
    {
        byte packedValue = buffer.readByte();

        return (packedValue == 1);
    }

    public static int encodedBooleanLength()
    {
        return 1;
    }

    // AMQP_BYTE_PROPERTY_PREFIX
    public static void writeByte(DataOutput buffer, byte aByte) throws IOException
    {
        buffer.writeByte(aByte);
    }

    public static byte readByte(DataInput buffer) throws IOException
    {
        return buffer.readByte();
    }

    public static int encodedByteLength()
    {
        return 1;
    }

    // AMQP_SHORT_PROPERTY_PREFIX
    public static void writeShort(DataOutput buffer, short aShort) throws IOException
    {
        buffer.writeShort(aShort);
    }

    public static short readShort(DataInput buffer) throws IOException
    {
        return buffer.readShort();
    }

    public static int encodedShortLength()
    {
        return 2;
    }

    // INTEGER_PROPERTY_PREFIX
    public static void writeInteger(DataOutput buffer, int aInteger) throws IOException
    {
        buffer.writeInt(aInteger);
    }

    public static int readInteger(DataInput buffer) throws IOException
    {
        return buffer.readInt();
    }

    public static int encodedIntegerLength()
    {
        return 4;
    }

    // AMQP_LONG_PROPERTY_PREFIX
    public static void writeLong(DataOutput buffer, long aLong) throws IOException
    {
        buffer.writeLong(aLong);
    }

    public static long readLong(DataInput buffer) throws IOException
    {
        return buffer.readLong();
    }

    public static int encodedLongLength()
    {
        return 8;
    }

    // Float_PROPERTY_PREFIX
    public static void writeFloat(DataOutput buffer, float aFloat) throws IOException
    {
        buffer.writeFloat(aFloat);
    }

    public static float readFloat(DataInput buffer) throws IOException
    {
        return buffer.readFloat();
    }

    public static int encodedFloatLength()
    {
        return 4;
    }

    // Double_PROPERTY_PREFIX
    public static void writeDouble(DataOutput buffer, Double aDouble) throws IOException
    {
        buffer.writeDouble(aDouble);
    }

    public static double readDouble(DataInput buffer) throws IOException
    {
        return buffer.readDouble();
    }

    public static int encodedDoubleLength()
    {
        return 8;
    }

    public static byte[] readBytes(DataInput buffer) throws IOException
    {
        long length = ((long)(buffer.readInt())) & 0xFFFFFFFFL;
        if (length == 0)
        {
            return null;
        }
        else
        {
            byte[] dataBytes = new byte[(int)length];
            buffer.readFully(dataBytes, 0, (int) length);

            return dataBytes;
        }
    }

    public static void writeBytes(DataOutput buffer, byte[] data) throws IOException
    {
        if (data != null)
        {
            // TODO: check length fits in an unsigned byte
            writeUnsignedInteger(buffer,  (long)data.length);
            buffer.write(data);
        }
        else
        {                                                    
            // really writing out unsigned byte
            writeUnsignedInteger(buffer, 0L);
        }
    }

    // CHAR_PROPERTY
    public static int encodedCharLength()
    {
        return encodedByteLength();
    }

    public static char readChar(DataInput buffer) throws IOException
    {
        // This is valid as we know that the Character is ASCII 0..127
        return (char) buffer.readByte();
    }

    public static void writeChar(DataOutput buffer, char character) throws IOException
    {
        // This is valid as we know that the Character is ASCII 0..127
        writeByte(buffer, (byte) character);
    }

    public static long readLongAsShortString(DataInput buffer) throws IOException
    {
        short length = (short) buffer.readUnsignedByte();
        short pos = 0;
        if (length == 0)
        {
            return 0L;
        }

        byte digit = buffer.readByte();
        boolean isNegative;
        long result = 0;
        if (digit == (byte) '-')
        {
            isNegative = true;
            pos++;
            digit = buffer.readByte();
        }
        else
        {
            isNegative = false;
        }

        result = digit - (byte) '0';
        pos++;

        while (pos < length)
        {
            pos++;
            digit = buffer.readByte();
            result = (result << 3) + (result << 1);
            result += digit - (byte) '0';
        }

        return result;
    }

    public static long readUnsignedInteger(DataInput buffer) throws IOException
    {
        long l = 0xFF & buffer.readByte();
        l <<= 8;
        l = l | (0xFF & buffer.readByte());
        l <<= 8;
        l = l | (0xFF & buffer.readByte());
        l <<= 8;
        l = l | (0xFF & buffer.readByte());

        return l;
    }

}
