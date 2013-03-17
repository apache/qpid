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

import java.nio.ByteBuffer;

public abstract class AbstractDescribedTypeWriter<V> implements ValueWriter<V>
{
    private int _length;
    private Registry _registry;
    private static final int LARGE_COMPOUND_THRESHOLD_COUNT = 10;
    private ValueWriter _delegate;
    private static final byte DESCRIBED_TYPE = (byte)0;

    public AbstractDescribedTypeWriter(final Registry registry)
    {
        _registry = registry;
    }

    enum State {
        FORMAT_CODE,
        DESCRIPTOR,
        DESCRIBED,
        DONE
    }

    private State _state = State.FORMAT_CODE;

    public int writeToBuffer(ByteBuffer buffer)
    {
        final int length = _length;

        if(length == -1)
        {
            writeFirstPass(buffer);
        }
        else
        {

            State state = _state;

            switch(state)
            {
                case FORMAT_CODE:
                    if(buffer.hasRemaining())
                    {
                        buffer.put(DESCRIBED_TYPE);
                        state = State.DESCRIPTOR;
                        _delegate = createDescriptorWriter();
                    }
                    else
                    {
                        break;
                    }

                case DESCRIPTOR:
                    if(buffer.hasRemaining())
                    {
                        _delegate.writeToBuffer(buffer);
                        if(_delegate.isComplete())
                        {
                            state = State.DESCRIBED;
                            _delegate = createDescribedWriter();
                        }
                        else
                        {
                            break;
                        }
                    }
                case DESCRIBED:
                    if(buffer.hasRemaining())
                    {
                        _delegate.writeToBuffer(buffer);
                        if(_delegate.isComplete())
                        {
                            state = State.DONE;
                            _delegate = null;
                        }
                        else
                        {
                            break;
                        }
                    }

            }

            _state = state;

        }

        return _length;
    }

    private void writeFirstPass(ByteBuffer buffer)
    {

        int length = 1;
        State state = State.FORMAT_CODE;

        ValueWriter descriptorWriter = createDescriptorWriter();
        if(buffer.hasRemaining())
        {
            buffer.put(DESCRIBED_TYPE);
            state = State.DESCRIPTOR;
            _delegate = descriptorWriter;
        }
        length += descriptorWriter.writeToBuffer(buffer);

        ValueWriter describedWriter = createDescribedWriter();

        if(descriptorWriter.isComplete())
        {
            state = State.DESCRIBED;
            _delegate = describedWriter;
        }

        length += describedWriter.writeToBuffer(buffer);

        if(describedWriter.isComplete())
        {
            _delegate = null;
            state = State.DONE;
        }

        _state = state;
        _length = length;
    }

    public void setValue(V value)
    {
        _length = -1;
        _delegate = null;
        _state = State.FORMAT_CODE;
        onSetValue(value);
    }

    public void setRegistry(Registry registry)
    {
        _registry = registry;
    }

    protected Registry getRegistry()
    {
        return _registry;
    }

    protected abstract void onSetValue(final V value);

    protected abstract void clear();

    protected abstract ValueWriter createDescribedWriter();

    protected abstract Object getDescriptor();

    protected final ValueWriter createDescriptorWriter()
    {
        return getRegistry().getValueWriter(getDescriptor());
    }

    public boolean isComplete()
    {
        return _state == State.DONE;
    }

    public boolean isCacheable()
    {
        return false;
    }
}