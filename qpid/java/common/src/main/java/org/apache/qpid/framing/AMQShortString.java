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

import org.apache.mina.common.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/**
 * A short string is a representation of an AMQ Short String
 * Short strings differ from the Java String class by being limited to on ASCII characters (0-127)
 * and thus can be held more effectively in a byte buffer.
 *
 */
public final class AMQShortString implements CharSequence, Comparable<AMQShortString>
{

    private static final ThreadLocal<Map<AMQShortString, WeakReference<AMQShortString>>> _localInternMap =
            new ThreadLocal<Map<AMQShortString, WeakReference<AMQShortString>>>()
            {
                protected Map<AMQShortString, WeakReference<AMQShortString>> initialValue()
                {
                    return new WeakHashMap<AMQShortString, WeakReference<AMQShortString>>();
                };
            };

    private static final Map<AMQShortString, WeakReference<AMQShortString>> _globalInternMap =
            new WeakHashMap<AMQShortString, WeakReference<AMQShortString>>();

    private static final Logger _logger = LoggerFactory.getLogger(AMQShortString.class);

    private final ByteBuffer _data;
    private int _hashCode;
    private final int _length;
    private static final char[] EMPTY_CHAR_ARRAY = new char[0];
    private char[] chars;
    private String str;

    public AMQShortString(byte[] data)
    {

        _data = ByteBuffer.wrap(data);
        _length = data.length;
    }

    public AMQShortString(String data)
    {
        this((data == null) ? EMPTY_CHAR_ARRAY : data.toCharArray());
        if (data != null)
        {
            _hashCode = data.hashCode();
        }
    }

    public AMQShortString(char[] data)
    {
        if (data == null)
        {
            throw new NullPointerException("Cannot create AMQShortString with null char[]");
        }

        final int length = data.length;
        final byte[] stringBytes = new byte[length];
        for (int i = 0; i < length; i++)
        {
            stringBytes[i] = (byte) (0xFF & data[i]);
        }

        _data = ByteBuffer.wrap(stringBytes);
        _data.rewind();
        _length = length;

    }

    public AMQShortString(CharSequence charSequence)
    {
        final int length = charSequence.length();
        final byte[] stringBytes = new byte[length];
        int hash = 0;
        for (int i = 0; i < length; i++)
        {
            stringBytes[i] = ((byte) (0xFF & charSequence.charAt(i)));
            hash = (31 * hash) + stringBytes[i];

        }

        _data = ByteBuffer.wrap(stringBytes);
        _data.rewind();
        _hashCode = hash;
        _length = length;

    }

    private AMQShortString(ByteBuffer data)
    {
        _data = data;
        _length = data.limit();

    }

    /**
     * Get the length of the short string
     * @return length of the underlying byte array
     */
    public int length()
    {
        return _length;
    }

    public char charAt(int index)
    {

        return (char) _data.get(index);

    }

    public CharSequence subSequence(int start, int end)
    {
        return new CharSubSequence(start, end);
    }

    public int writeToByteArray(byte[] encoding, int pos)
    {
        final int size = length();
        encoding[pos++] = (byte) length();
        for (int i = 0; i < size; i++)
        {
            encoding[pos++] = _data.get(i);
        }

        return pos;
    }

    public static AMQShortString readFromByteArray(byte[] byteEncodedDestination, int pos)
    {

        final byte len = byteEncodedDestination[pos];
        if (len == 0)
        {
            return null;
        }

        ByteBuffer data = ByteBuffer.wrap(byteEncodedDestination, pos + 1, len).slice();

        return new AMQShortString(data);
    }

    public static AMQShortString readFromBuffer(ByteBuffer buffer)
    {
        final short length = buffer.getUnsigned();
        if (length == 0)
        {
            return null;
        }
        else
        {
            ByteBuffer data = buffer.slice();
            data.limit(length);
            data.rewind();
            buffer.skip(length);

            return new AMQShortString(data);
        }
    }

    public byte[] getBytes()
    {

        if (_data.buf().hasArray() && (_data.arrayOffset() == 0))
        {
            return _data.array();
        }
        else
        {
            final int size = length();
            byte[] b = new byte[size];
            ByteBuffer buf = _data.duplicate();
            buf.rewind();
            buf.get(b);

            return b;
        }

    }

    public void writeToBuffer(ByteBuffer buffer)
    {

        final int size = length();
        if (size != 0)
        {

            buffer.put((byte) size);
            if (_data.buf().hasArray())
            {
                buffer.put(_data.array(), _data.arrayOffset(), length());
            }
            else
            {

                for (int i = 0; i < size; i++)
                {

                    buffer.put(_data.get(i));
                }
            }
        }
        else
        {
            // really writing out unsigned byte
            buffer.put((byte) 0);
        }

    }

    private final class CharSubSequence implements CharSequence
    {
        private final int _offset;
        private final int _end;

        public CharSubSequence(final int offset, final int end)
        {
            _offset = offset;
            _end = end;
        }

        public int length()
        {
            return _end - _offset;
        }

        public char charAt(int index)
        {
            return AMQShortString.this.charAt(index + _offset);
        }

        public CharSequence subSequence(int start, int end)
        {
            return new CharSubSequence(start + _offset, end + _offset);
        }
    }

    public char[] asChars()
    {
        if (chars == null)
        {
            final int size = length();
            chars = new char[size];

            for (int i = 0; i < size; i++)
            {
                chars[i] = (char) _data.get(i);
            }
        }

        return chars;
    }

    public String asString()
    {
        if (str == null)
        {
            str = new String(asChars());
        }

        return str;
    }

    public boolean equals(Object o)
    {
        if (o == null)
        {
            return false;
        }

        if (o == this)
        {
            return true;
        }

        if (o instanceof AMQShortString)
        {

            final AMQShortString otherString = (AMQShortString) o;

            if ((_hashCode != 0) && (otherString._hashCode != 0) && (_hashCode != otherString._hashCode))
            {
                return false;
            }

            return _data.equals(otherString._data);

        }

        return (o instanceof CharSequence) && equals((CharSequence) o);

    }

    public boolean equals(CharSequence s)
    {
        if (s == null)
        {
            return false;
        }

        if (s.length() != length())
        {
            return false;
        }

        for (int i = 0; i < length(); i++)
        {
            if (charAt(i) != s.charAt(i))
            {
                return false;
            }
        }

        return true;
    }

    public int hashCode()
    {
        int hash = _hashCode;
        if (hash == 0)
        {
            final int size = length();

            for (int i = 0; i < size; i++)
            {
                hash = (31 * hash) + _data.get(i);
            }

            _hashCode = hash;
        }

        return hash;
    }

    public void setDirty()
    {
        _hashCode = 0;
    }

    public String toString()
    {
        return asString();
    }

    public int compareTo(AMQShortString name)
    {
        if (name == null)
        {
            return 1;
        }
        else
        {

            if (name.length() < length())
            {
                return -name.compareTo(this);
            }

            for (int i = 0; i < length(); i++)
            {
                final byte d = _data.get(i);
                final byte n = name._data.get(i);
                if (d < n)
                {
                    return -1;
                }

                if (d > n)
                {
                    return 1;
                }
            }

            return (length() == name.length()) ? 0 : -1;
        }
    }

    public AMQShortString intern()
    {

        hashCode();

        Map<AMQShortString, WeakReference<AMQShortString>> localMap =
                _localInternMap.get();

        WeakReference<AMQShortString> ref = localMap.get(this);
        AMQShortString internString;

        if(ref != null)
        {
            internString = ref.get();
            if(internString != null)
            {
                return internString;
            }
        }


        synchronized(_globalInternMap)
        {

            ref = _globalInternMap.get(this);
            if((ref == null) || ((internString = ref.get()) == null))
            {
                internString = new AMQShortString(getBytes());
                ref = new WeakReference(internString);
                _globalInternMap.put(internString, ref);
            }

        }
        localMap.put(internString, ref);
        return internString;

    }
}
