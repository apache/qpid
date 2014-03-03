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
package org.apache.qpid.amqp_1_0.codec;


final class BinaryString
{

    private byte[] _data;
    private int _offset;
    private int _size;
    private int _hashCode;

    BinaryString(final byte[] data, final int offset, final int size)
    {

        setData(data, offset, size);
    }

    BinaryString()
    {
    }

    void setData(byte[] data, int offset, int size)
    {
        _data = data;
        _offset = offset;
        _size = size;
        int hc = 0;
        for (int i = 0; i < size; i++)
        {
            hc = 31*hc + (0xFF & data[offset + i]);
        }
        _hashCode = hc;
    }


    public final int hashCode()
    {
        return _hashCode;
    }

    public final boolean equals(Object o)
    {
        if(!(o instanceof BinaryString))
        {
            return false;
        }

        BinaryString buf = (BinaryString) o;
        final int size = _size;
        if (size != buf._size)
        {
            return false;
        }

        final byte[] myData = _data;
        final byte[] theirData = buf._data;
        int myOffset = _offset;
        int theirOffset = buf._offset;
        final int myLimit = myOffset + size;

        while(myOffset < myLimit)
        {
            if (myData[myOffset++] != theirData[theirOffset++])
            {
                return false;
            }
        }

        return true;
    }


}
