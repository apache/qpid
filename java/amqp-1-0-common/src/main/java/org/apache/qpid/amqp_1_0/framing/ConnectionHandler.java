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
package org.apache.qpid.amqp_1_0.framing;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.qpid.amqp_1_0.codec.FrameWriter;
import org.apache.qpid.amqp_1_0.codec.ProtocolHandler;
import org.apache.qpid.amqp_1_0.codec.ProtocolHeaderHandler;
import org.apache.qpid.amqp_1_0.codec.ValueHandler;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;
import org.apache.qpid.amqp_1_0.transport.BytesProcessor;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.FrameOutputHandler;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.transport.Open;

public class ConnectionHandler
{
    private final ConnectionEndpoint _connection;
    private ProtocolHandler _delegate;

    private static final Logger FRAME_LOGGER = Logger.getLogger("FRM");
    private static final Logger RAW_LOGGER = Logger.getLogger("RAW");

    public ConnectionHandler(final ConnectionEndpoint connection)
    {
        _connection = connection;
        _delegate = new ProtocolHeaderHandler(connection);
    }


    public boolean parse(ByteBuffer in)
    {

        if(RAW_LOGGER.isLoggable(Level.FINE))
        {
            Binary b = new Binary(in.array(),in.arrayOffset()+in.position(),in.remaining());
            RAW_LOGGER.fine("RECV [" + _connection.getRemoteAddress() + "] : " + b.toString());
        }

        while(in.hasRemaining() && !isDone())
        {
            _delegate = _delegate.parse(in);

        }
        return isDone();
    }

    public boolean isDone()
    {
        return _delegate.isDone();
    }


    // ----------------------------------------------------------------

    public static class FrameOutput<T> implements FrameOutputHandler<T>
    {

        private static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.wrap(new byte[0]);
        private final BlockingQueue<AMQFrame<T>> _queue = new ArrayBlockingQueue<AMQFrame<T>>(100);
        private ConnectionEndpoint _conn;

        private final AMQFrame<T> _endOfFrameMarker = new AMQFrame<T>(null)
        {
            @Override public short getChannel()
            {
                throw new UnsupportedOperationException();
            }

            @Override public byte getFrameType()
            {
                throw new UnsupportedOperationException();
            }
        };

        private boolean _setForClose;
        private boolean _closed;
        private long _nextHeartbeat;

        public FrameOutput(final ConnectionEndpoint conn)
        {
            _conn = conn;
        }

        public FrameSource asFrameSource()
        {
            return new FrameSource()
            {
                @Override
                public AMQFrame getNextFrame(final boolean wait)
                {
                    return FrameOutput.this.getNextFrame(wait);
                }

                @Override
                public boolean closed()
                {
                    return FrameOutput.this.closed();
                }

                @Override
                public void close()
                {
                    FrameOutput.this.immediateClose();
                }
            };
        }

        private void immediateClose()
        {
            synchronized (_conn.getLock())
            {
                _closed = true;
                _conn.getLock().notifyAll();
            }
        }

        public boolean canSend()
        {
            return _queue.remainingCapacity() != 0;
        }

        public void send(AMQFrame<T> frame)
        {
            send(frame, null);
        }

        public void send(final AMQFrame<T> frame, final ByteBuffer payload)
        {
            synchronized(_conn.getLock())
            {
                try
                {
// TODO HACK - check frame length
                    int size = _conn.getDescribedTypeRegistry()
                            .getValueWriter(frame.getFrameBody()).writeToBuffer(EMPTY_BYTEBUFFER) + 8;

                    if(size > _conn.getMaxFrameSize())
                    {
                        throw new OversizeFrameException(frame, size);
                    }

                    while(!_queue.offer(frame))
                    {
                        _conn.getLock().wait(1000L);

                    }
                    _conn.getLock().notifyAll();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }


        public void close()
        {
            synchronized (_conn.getLock())
            {
                if(!_queue.offer(_endOfFrameMarker))
                {
                    _setForClose = true;
                }
                _conn.getLock().notifyAll();
            }
        }

        public AMQFrame<T> getNextFrame(final boolean wait)
        {
            synchronized(_conn.getLock())
            {
                long time = System.currentTimeMillis();
                try
                {
                    AMQFrame frame = null;
                    while(!closed() && (frame = _queue.poll()) == null && wait)
                    {
                        _conn.getLock().wait(_conn.getIdleTimeout()/2);

                        if(_conn.getIdleTimeout()>0)
                        {
                            time = System.currentTimeMillis();

                            if(frame == null && time > _nextHeartbeat)
                            {
                                frame = new TransportFrame((short) 0,null);
                                break;
                            }
                        }
                    }




                    if(frame != null)
                    {
                        _nextHeartbeat = time + _conn.getIdleTimeout()/2;

                    }
                    if(frame == _endOfFrameMarker)
                    {
                        _closed = true;
                        frame = null;
                    }
                    else if(_setForClose && frame != null)
                    {
                        _setForClose = !_queue.offer(_endOfFrameMarker);
                    }


                    if(frame != null && FRAME_LOGGER.isLoggable(Level.FINE))
                    {
                        FRAME_LOGGER.fine("SEND[" + _conn.getRemoteAddress() + "|" + frame.getChannel() + "] : " + frame.getFrameBody());
                    }

                    _conn.getLock().notifyAll();

                    return frame;
                }
                catch (InterruptedException e)
                {
                    _conn.setClosedForOutput(true);
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    return null;
                }
            }
        }

        public boolean closed()
        {
            return _closed;
        }
    }

    public static interface FrameSource<T>
    {
        AMQFrame<T> getNextFrame(boolean wait);
        boolean closed();

        void close();
    }


    public static interface BytesSource
    {
        void getBytes(BytesProcessor processor, boolean wait);
        boolean closed();

        void close();
    }

    public static class FrameToBytesSourceAdapter implements BytesSource
    {

        private final FrameSource _frameSource;
        private final FrameWriter _writer;
        private static final int BUF_SIZE = 1<<16;
        private final byte[] _bytes = new byte[BUF_SIZE];
        private final ByteBuffer _buffer = ByteBuffer.wrap(_bytes);

        public FrameToBytesSourceAdapter(final FrameSource frameSource, ValueWriter.Registry registry)
        {
            _frameSource = frameSource;
            _writer =  new FrameWriter(registry);
        }

        public void getBytes(final BytesProcessor processor, final boolean wait)
        {

            AMQFrame frame;

            if(_buffer.position() == 0 && !_frameSource.closed())
            {
                if(!_writer.isComplete())
                {
                    _writer.writeToBuffer(_buffer);
                }

                while(_buffer.hasRemaining())
                {

                    if((frame = _frameSource.getNextFrame(wait && _buffer.position()==0)) != null)
                    {
                        _writer.setValue(frame);

                        _writer.writeToBuffer(_buffer);
                    }
                    else
                    {
                        break;
                    }
                }
                _buffer.flip();
            }
            if(_buffer.limit() != 0)
            {
                processor.processBytes(_buffer);
                if(_buffer.remaining() == 0)
                {
                    _buffer.clear();
                }
            }
        }

        public boolean closed()
        {
            return _buffer.position() == 0 && _frameSource.closed();
        }

        @Override
        public void close()
        {
            _frameSource.close();
        }
    }


    public static class HeaderBytesSource implements BytesSource
    {

        private final ByteBuffer _buffer;
        private ConnectionEndpoint _conn;

        public HeaderBytesSource(ConnectionEndpoint conn, byte... headerBytes)
        {
            _conn = conn;
            _buffer = ByteBuffer.wrap(headerBytes);
        }

        public void getBytes(final BytesProcessor processor, final boolean wait)
        {
            processor.processBytes(_buffer);
        }

        public boolean closed()
        {
            return !_buffer.hasRemaining();
        }

        @Override
        public void close()
        {
        }
    }

    public static class SequentialBytesSource implements BytesSource
    {
        private Queue<BytesSource> _sources = new LinkedList<BytesSource>();

        public SequentialBytesSource(BytesSource... sources)
        {
            _sources.addAll(Arrays.asList(sources));
        }

        public synchronized void addSource(BytesSource source)
        {
            _sources.add(source);
        }

        public synchronized void getBytes(final BytesProcessor processor, final boolean wait)
        {
            BytesSource src = _sources.peek();
            while (src != null && src.closed())
            {
                _sources.poll();
                src = _sources.peek();
            }

            if(src != null)
            {
                src.getBytes(processor, wait);
            }
        }

        public boolean closed()
        {
            return _sources.isEmpty();
        }

        @Override
        public void close()
        {
            BytesSource src = _sources.peek();
            while (src != null)
            {
                src.close();
                _sources.poll();
                src = _sources.peek();
            }

        }
    }


    public static class SequentialFrameSource implements FrameSource
    {
        private Queue<FrameSource> _sources = new LinkedList<FrameSource>();

        public SequentialFrameSource(FrameSource... sources)
        {
            _sources.addAll(Arrays.asList(sources));
        }

        public synchronized void addSource(FrameSource source)
        {
            _sources.add(source);
        }

        @Override
        public synchronized AMQFrame getNextFrame(final boolean wait)
        {
            FrameSource src = _sources.peek();
            while (src != null && src.closed())
            {
                _sources.poll();
                src = _sources.peek();
            }

            if(src != null)
            {
                return src.getNextFrame(wait);
            }
            else
            {
                return null;
            }
        }

        public boolean closed()
        {
            return _sources.isEmpty();
        }

        @Override
        public void close()
        {
            FrameSource src = _sources.peek();
            while (src != null)
            {
                src.close();
                _sources.poll();
                src = _sources.peek();
            }

        }
    }


    public static class BytesOutputHandler implements Runnable, BytesProcessor
    {

        private final OutputStream _outputStream;
        private BytesSource _bytesSource;
        private boolean _closed;
        private ConnectionEndpoint _conn;
        private ExceptionHandler _exceptionHandler;

        public BytesOutputHandler(OutputStream outputStream, BytesSource source, ConnectionEndpoint conn, ExceptionHandler exceptionHandler)
        {
            _outputStream = outputStream;
            _bytesSource = source;
            _conn = conn;
            _exceptionHandler = exceptionHandler;
        }

        public void run()
        {

            final BytesSource bytesSource = _bytesSource;

            while(!(_closed || bytesSource.closed()))
            {
                _bytesSource.getBytes(this, true);
            }

        }

        public void processBytes(final ByteBuffer buf)
        {
            try
            {
                if(RAW_LOGGER.isLoggable(Level.FINE))
                {
                    Binary bin = new Binary(buf.array(),buf.arrayOffset()+buf.position(), buf.limit()-buf.position());
                    RAW_LOGGER.fine("SEND["+ _conn.getRemoteAddress() +"] : " + bin.toString());
                }
                synchronized (_outputStream)
                {
                    _outputStream.write(buf.array(),buf.arrayOffset()+buf.position(), buf.limit()-buf.position());
                }
                buf.position(buf.limit());
            }
            catch (IOException e)
            {
                _closed = true;
                _bytesSource.close();
                _exceptionHandler.handleException(e);
            }
        }
    }


    public static class OutputHandler implements Runnable
    {



        private final OutputStream _outputStream;
        private FrameSource _frameSource;

        private static final int BUF_SIZE = 1<<16;
        private ValueWriter.Registry _registry;


        public OutputHandler(OutputStream outputStream, FrameSource source, ValueWriter.Registry registry)
        {
            _outputStream = outputStream;
            _frameSource = source;
            _registry = registry;
        }

        public void run()
        {
            int i=0;


            try
            {

                byte[] buffer = new byte[BUF_SIZE];
                ByteBuffer buf = ByteBuffer.wrap(buffer);

                buf.put((byte)'A');
                buf.put((byte)'M');
                buf.put((byte)'Q');
                buf.put((byte)'P');
                buf.put((byte) 0);
                buf.put((byte) 1);
                buf.put((byte) 0);
                buf.put((byte) 0);



                final FrameSource frameSource = _frameSource;

                AMQFrame frame;
                FrameWriter writer =  new FrameWriter(_registry);

                while(!frameSource.closed())
                {

                    if(!writer.isComplete())
                    {
                        writer.writeToBuffer(buf);
                    }

                    while(buf.hasRemaining())
                    {

                        if((frame = frameSource.getNextFrame(buf.position()==0)) != null)
                        {
                            writer.setValue(frame);

                            int size = writer.writeToBuffer(buf);

                        }
                        else
                        {
                            break;
                        }
                    }

                    if(buf.limit() != 0)
                    {
                        _outputStream.write(buffer,0, buf.position());
                        buf.clear();
                    }
                }

            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }
    }

    public static void main(String[] args) throws AmqpErrorException
    {
        byte[] buffer = new byte[76];
        ByteBuffer buf = ByteBuffer.wrap(buffer);
        AMQPDescribedTypeRegistry registry = AMQPDescribedTypeRegistry.newInstance()
                .registerTransportLayer()
                .registerMessagingLayer()
                .registerTransactionLayer();

        Open open = new Open();
        // Open(container_id="venture", channel_max=10, hostname="foo", offered_capabilities=[Symbol("one"), Symbol("two"), Symbol("three")])
        open.setContainerId("venture");
        open.setChannelMax(UnsignedShort.valueOf((short) 10));
        open.setHostname("foo");
        open.setOfferedCapabilities(new Symbol[] {Symbol.valueOf("one"),Symbol.valueOf("two"),Symbol.valueOf("three")});

        ValueWriter<Open> writer = registry.getValueWriter(open);

        System.out.println("------ Encode (time in ms for 1 million opens)");
        Long myLong = Long.valueOf(32);
        ValueWriter<Long> writer2 = registry.getValueWriter(myLong);
        Double myDouble = Double.valueOf(3.14159265359);
        ValueWriter<Double> writer3 = registry.getValueWriter(myDouble);
        for(int n = 0; n < 1/*00*/; n++)
        {
            long startTime = System.currentTimeMillis();
            for(int i = 1/*000000*/; i !=0; i--)
            {
                buf.position(0);
                writer.setValue(open);
                writer.writeToBuffer(buf);
                writer2.setValue(myLong);
                writer.writeToBuffer(buf);
                writer3.setValue(myDouble);
                writer3.writeToBuffer(buf);


            }
            long midTime = System.currentTimeMillis();
            System.out.println((midTime - startTime));

        }


        ValueHandler handler = new ValueHandler(registry);
        System.out.println("------ Decode (time in ms for 1 million opens)");
        for(int n = 0; n < 100; n++)
        {
            long startTime = System.currentTimeMillis();
            for(int i = 1000000; i !=0; i--)
            {
                buf.flip();
                handler.parse(buf);
                handler.parse(buf);
                handler.parse(buf);

            }
            long midTime = System.currentTimeMillis();
            System.out.println((midTime - startTime));
        }


    }

}
