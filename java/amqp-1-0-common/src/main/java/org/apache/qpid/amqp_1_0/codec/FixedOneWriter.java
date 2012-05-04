/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.amqp_1_0.codec;

import java.nio.ByteBuffer;


public abstract class FixedOneWriter<T> implements ValueWriter<T>
{
    protected int _written = 2;
    protected byte _value;

    public int writeToBuffer(ByteBuffer buffer)
    {

        switch(_written)
        {
            case 0:
                if(buffer.hasRemaining())
                {
                    buffer.put(getFormatCode());
                }
                else
                {
                    break;
                }
            case 1:
                if(buffer.hasRemaining())
                {
                    buffer.put(_value);
                    _written = 2;
                }
                else
                {
                    _written = 1;
                }

        }

        return 2;
    }

    protected abstract byte getFormatCode();

    public boolean isComplete()
    {
        return _written == 2;
    }

    public boolean isCacheable()
    {
        return true;
    }

    public void setValue(final T value)
    {
        _written = 0;
        _value = convertToByte(value);
    }

    protected abstract byte convertToByte(final T value);
}
