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

public abstract class AbstractMapWriter<V> extends CompoundWriter<V>
{
    private boolean onKey;

    public AbstractMapWriter(Registry registry)
    {
        super(registry);
    }

    @Override
    protected byte getFourOctetEncodingCode()
    {
        return (byte)0xd1;
    }

    @Override
    protected byte getSingleOctetEncodingCode()
    {
        return (byte)0xc1;
    }

    @Override
    protected final int getCount()
    {
        return 2 * getMapCount();
    }

    protected abstract int getMapCount();

    @Override
    protected final boolean hasNext()
    {
        return onKey || hasMapNext();
    }

    protected abstract boolean hasMapNext();

    @Override
    protected final Object next()
    {
        if(onKey = !onKey)
        {
            return nextKey();
        }
        else
        {
            return nextValue();
        }
    }

    protected abstract Object nextValue();

    protected abstract Object nextKey();

    @Override
    protected final void clear()
    {
        onKey = false;
        onClear();
    }

    protected abstract void onClear();

    @Override
    protected final void reset()
    {
        onKey = false;
        onReset();
    }

    protected abstract void onReset();
}
