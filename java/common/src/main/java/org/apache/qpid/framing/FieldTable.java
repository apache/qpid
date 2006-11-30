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

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;

import java.util.*;

/**
 * From the protocol document:
 * field-table      = short-integer *field-value-pair
 * field-value-pair = field-name field-value
 * field-name       = short-string
 * field-value      = 'S' long-string
 * / 'I' long-integer
 * / 'D' decimal-value
 * / 'T' long-integer
 * decimal-value    = decimals long-integer
 * decimals         = OCTET
 */
public class FieldTable extends LinkedHashMap
{
    private static final Logger _logger = Logger.getLogger(FieldTable.class);
    private long _encodedSize = 0;

    public FieldTable()
    {
        super();
    }

    /**
     * Construct a new field table.
     *
     * @param buffer the buffer from which to read data. The length byte must be read already
     * @param length the length of the field table. Must be > 0.
     * @throws AMQFrameDecodingException if there is an error decoding the table
     */
    public FieldTable(ByteBuffer buffer, long length) throws AMQFrameDecodingException
    {
        super();
        final boolean debug = _logger.isDebugEnabled();
        assert length > 0;
        _encodedSize = length;
        int sizeRead = 0;
        while (sizeRead < _encodedSize)
        {
            int sizeRemaining = buffer.remaining();
            final String key = EncodingUtils.readShortString(buffer);
            // TODO: use proper charset decoder
            byte iType = buffer.get();
            final char type = (char) iType;
            Object value;
            switch (type)
            {
                case'S':
                    value = EncodingUtils.readLongString(buffer);
                    break;
                case'I':
                    value = new Long(buffer.getUnsignedInt());
                    break;
                default:
                    String msg = "Field '" + key + "' - unsupported field table type: " + type;
                    //some extra debug information...
                    msg += " (" + iType + "), length=" + length + ", sizeRead=" + sizeRead + ", sizeRemaining=" + sizeRemaining;
                    throw new AMQFrameDecodingException(msg);
            }
            sizeRead += (sizeRemaining - buffer.remaining());

            if (debug)
            {
                _logger.debug("FieldTable::FieldTable(buffer," + length + "): Read type '" + type + "', key '" + key + "', value '" + value + "' (now read " + sizeRead + " of " + length + " encoded bytes)...");
            }

            // we deliberately want to call put in the parent class since we do
            // not need to do the size calculations
            super.put(key, value);
        }

        if (debug)
        {
            _logger.debug("FieldTable::FieldTable(buffer," + length + "): Done.");
        }
    }

    public void writeToBuffer(ByteBuffer buffer)
    {
        final boolean debug = _logger.isDebugEnabled();

        if (debug)
        {
            _logger.debug("FieldTable::writeToBuffer: Writing encoded size of " + _encodedSize + "...");
        }

        // write out the total length, which we have kept up to date as data is added
        EncodingUtils.writeUnsignedInteger(buffer, _encodedSize);
        final Iterator it = this.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry me = (Map.Entry) it.next();
            String key = (String) me.getKey();

            EncodingUtils.writeShortStringBytes(buffer, key);
            Object value = me.getValue();

            if (debug)
            {
                _logger.debug("FieldTable::writeToBuffer: Writing key '" + key + "' of type " + value.getClass() + ", value '" + value + "'...");
            }

            if (value instanceof byte[])
            {
                buffer.put((byte) 'S');
                EncodingUtils.writeLongstr(buffer, (byte[]) value);
            }
            else if (value instanceof String)
            {
                // TODO: look at using proper charset encoder
                buffer.put((byte) 'S');
                EncodingUtils.writeLongStringBytes(buffer, (String) value);
            }
            else if (value instanceof Long)
            {
                // TODO: look at using proper charset encoder
                buffer.put((byte) 'I');
                EncodingUtils.writeUnsignedInteger(buffer, ((Long) value).longValue());
            }
            else
            {
                // Should never get here
                throw new IllegalArgumentException("Key '" + key + "': Unsupported type in field table, type: " + ((value == null) ? "null-object" : value.getClass()));
            }
        }

        if (debug)
        {
            _logger.debug("FieldTable::writeToBuffer: Done.");
        }
    }

    public byte[] getDataAsBytes()
    {
        final ByteBuffer buffer = ByteBuffer.allocate((int) _encodedSize); // XXX: Is cast a problem?
        final Iterator it = this.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry me = (Map.Entry) it.next();
            String key = (String) me.getKey();
            EncodingUtils.writeShortStringBytes(buffer, key);
            Object value = me.getValue();
            if (value instanceof byte[])
            {
                buffer.put((byte) 'S');
                EncodingUtils.writeLongstr(buffer, (byte[]) value);
            }
            else if (value instanceof String)
            {
                // TODO: look at using proper charset encoder
                buffer.put((byte) 'S');
                EncodingUtils.writeLongStringBytes(buffer, (String) value);
            }
            else if (value instanceof char[])
            {
                // TODO: look at using proper charset encoder
                buffer.put((byte) 'S');
                EncodingUtils.writeLongStringBytes(buffer, (char[]) value);
            }
            else if (value instanceof Long || value instanceof Integer)
            {
                // TODO: look at using proper charset encoder
                buffer.put((byte) 'I');
                EncodingUtils.writeUnsignedInteger(buffer, ((Long) value).longValue());
            }
            else
            {
                // Should never get here
                assert false;
            }
        }
        final byte[] result = new byte[(int) _encodedSize];
        buffer.flip();
        buffer.get(result);
        buffer.release();
        return result;
    }

    public Object put(Object key, Object value)
    {
        final boolean debug = _logger.isDebugEnabled();

        if (key == null)
        {
            throw new IllegalArgumentException("All keys must be Strings - was passed: null");
        }
        else if (!(key instanceof String))
        {
            throw new IllegalArgumentException("All keys must be Strings - was passed: " + key.getClass());
        }

        Object existing;

        if ((existing = super.remove(key)) != null)
        {
            if (debug)
            {
                _logger.debug("Found duplicate of key '" + key + "', previous value '" + existing + "' (" + existing.getClass() + "), to be replaced by '" + value + "', (" + value.getClass() + ") - stack trace of source of duplicate follows...", new Throwable().fillInStackTrace());
            }

            // If we are in effect deleting the value (see comment on null values being deleted
            // below) then we also need to remove the name from the encoding length.
            if (value == null)
            {
                _encodedSize -= EncodingUtils.encodedShortStringLength((String) key);
            }

            // FIXME: Should be able to short-cut this process if the old and new values are
            // the same object and/or type and size...
            _encodedSize -= getEncodingSize(existing);
        }
        else
        {
            if (value != null)
            {
                _encodedSize += EncodingUtils.encodedShortStringLength((String) key);
            }
        }

        // For now: Setting a null value is the equivalent of deleting it.
        // This is ambiguous in the JMS spec and needs thrashing out and potentially
        // testing against other implementations.
        if (value != null)
        {
            _encodedSize += getEncodingSize(value);
        }

        return super.put(key, value);
    }

    public Object remove(Object key)
    {
        if (super.containsKey(key))
        {
            final Object value = super.remove(key);
            _encodedSize -= EncodingUtils.encodedShortStringLength((String) key);

            // This check is, for now, unnecessary (we don't store null values).
            if (value != null)
            {
                _encodedSize -= getEncodingSize(value);
            }

            return value;
        }
        else
        {
            return null;
        }
    }

    /**
     * @return unsigned integer
     */
    public long getEncodedSize()
    {
        return _encodedSize;
    }

    /**
     * @return integer
     */
    private static int getEncodingSize(Object value)
    {
        int encodingSize;

        // the extra byte if for the type indicator that is written out
        if (value instanceof String)
        {
            encodingSize = 1 + EncodingUtils.encodedLongStringLength((String) value);
        }
        else if (value instanceof char[])
        {
            encodingSize = 1 + EncodingUtils.encodedLongStringLength((char[]) value);
        }
        else if (value instanceof Integer)
        {
            encodingSize = 1 + 4;
        }
        else if (value instanceof Long)
        {
            encodingSize = 1 + 4;
        }
        else
        {
            throw new IllegalArgumentException("Unsupported type in field table: " + value.getClass());
        }

        return encodingSize;
    }
}
