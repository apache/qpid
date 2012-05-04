/*
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
 */

package org.apache.qpid.amqp_1_0.transport;

import org.apache.qpid.amqp_1_0.codec.ValueWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.type.FrameBody;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class AMQPFrameTransport implements FrameTransport<AMQFrame<FrameBody>>, FrameOutputHandler<FrameBody>
{
    private final Object _inputLock = new Object();
    private final Object _outputLock = new Object();

    private volatile boolean _inputOpen = true;
    private volatile boolean _outputOpen = true;

    private final ConnectionEndpoint _endpoint;
    private final BlockingQueue<AMQFrame<FrameBody>> _queue = new ArrayBlockingQueue<AMQFrame<FrameBody>>(100);
    private StateChangeListener _inputListener;
    private StateChangeListener _outputListener;


    public AMQPFrameTransport(final ConnectionEndpoint endpoint)
    {
        _endpoint = endpoint;

        _endpoint.setFrameOutputHandler(this);
    }

    public boolean isOpenForInput()
    {
        return _inputOpen;
    }

    public void closeForInput()
    {
        synchronized(_inputLock)
        {
            _inputOpen = false;
            _inputLock.notifyAll();
        }
    }

    public void processIncomingFrame(final AMQFrame<FrameBody> frame)
    {
        frame.getFrameBody().invoke(frame.getChannel(), _endpoint);
    }


    public boolean canSend()
    {
        return _queue.remainingCapacity() != 0;
    }

    public void send(AMQFrame<FrameBody> frame)
    {
        send(frame, null);
    }


    public void send(final AMQFrame<FrameBody> frame, final ByteBuffer payload)
    {
        synchronized(_endpoint.getLock())
        {
            boolean empty = _queue.isEmpty();
            try
            {

                while(!_queue.offer(frame))
                {
                    _endpoint.getLock().wait(1000L);

                }
                if(empty && _outputListener != null)
                {
                    _outputListener.onStateChange(true);
                }

                _endpoint.getLock().notifyAll();
            }
            catch (InterruptedException e)
            {

            }
        }
    }

    public void close()
    {
        synchronized (_endpoint.getLock())
        {
            _endpoint.getLock().notifyAll();
        }
    }

    public AMQFrame<FrameBody> getNextFrame()
    {
        synchronized(_endpoint.getLock())
        {
            AMQFrame<FrameBody> frame = null;
            if(isOpenForOutput())
            {
                frame = _queue.poll();
            }
            return frame;
        }
    }

    public void closeForOutput()
    {
        synchronized (_outputLock)
        {
            _outputOpen = false;
            _outputLock.notifyAll();
        }
    }

    public boolean isOpenForOutput()
    {
        return _outputOpen;
    }

    public ValueWriter.Registry getRegistry()
    {
        return _endpoint.getDescribedTypeRegistry();
    }

    public void setInputStateChangeListener(final StateChangeListener listener)
    {
        _inputListener = listener;
    }

    public void setOutputStateChangeListener(final StateChangeListener listener)
    {
        _outputListener = listener;
    }

    public void setVersion(final byte major, final byte minor, final byte revision)
    {

    }

    public byte getMajorVersion()
    {
        return _endpoint.getMajorVersion();
    }

    public byte getMinorVersion()
    {
        return _endpoint.getMinorVersion();
    }

    public byte getRevision()
    {
        return _endpoint.getRevision();
    }
}
