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
package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.framing.ConnectionHandler;
import org.apache.qpid.amqp_1_0.transport.AMQPTransport;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.Container;
import org.apache.qpid.amqp_1_0.transport.StateChangeListener;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Connection
{
    private static final Logger RAW_LOGGER = Logger.getLogger("RAW");

    private String _address;
    private ConnectionEndpoint _conn;
    private int _sessionCount;


    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password) throws ConnectionException
    {
        this(address, port, username, password, 65536);
    }
    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final int maxFrameSize) throws ConnectionException
    {
        this(address,port,username,password,maxFrameSize,new Container());
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final int maxFrameSize,
                  final Container container) throws ConnectionException
    {

        _address = address;

        try
        {
            final Socket s = new Socket(address, port);


            Principal principal = username == null ? null : new Principal()
            {

                public String getName()
                {
                    return username;
                }
            };
            _conn = new ConnectionEndpoint(container, principal, password);
            _conn.setDesiredMaxFrameSize(UnsignedInteger.valueOf(maxFrameSize));
            _conn.setRemoteAddress(s.getRemoteSocketAddress());



            ConnectionHandler.FrameOutput<FrameBody> out = new ConnectionHandler.FrameOutput<FrameBody>(_conn);


            final OutputStream outputStream = s.getOutputStream();

            ConnectionHandler.BytesSource src;

            if(_conn.requiresSASL())
            {
                ConnectionHandler.FrameOutput<SaslFrameBody> saslOut = new ConnectionHandler.FrameOutput<SaslFrameBody>(_conn);

                src =  new ConnectionHandler.SequentialBytesSource(new ConnectionHandler.HeaderBytesSource(_conn, (byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)3,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(saslOut,_conn.getDescribedTypeRegistry()),
                                                                   new ConnectionHandler.HeaderBytesSource(_conn, (byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)0,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(out,_conn.getDescribedTypeRegistry())
                );

                _conn.setSaslFrameOutput(saslOut);
            }
            else
            {
                src =  new ConnectionHandler.SequentialBytesSource(new ConnectionHandler.HeaderBytesSource(_conn,(byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)0,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(out,_conn.getDescribedTypeRegistry())
                );
            }


            //ConnectionHandler.OutputHandler outputHandler = new ConnectionHandler.OutputHandler(outputStream, out, _conn.getDescribedTypeRegistry());
            ConnectionHandler.BytesOutputHandler outputHandler = new ConnectionHandler.BytesOutputHandler(outputStream, src, _conn);
            Thread outputThread = new Thread(outputHandler);
            outputThread.setDaemon(true);
            outputThread.start();
            _conn.setFrameOutputHandler(out);



            final ConnectionHandler handler = new ConnectionHandler(_conn);
            final InputStream inputStream = s.getInputStream();

            //final AMQPTransport transport = new AMQPTransport(new AMQPFrameTransport(_conn));

            Thread inputThread = new Thread(new Runnable()
            {

                public void run()
                {
                    try
                    {
                        doRead(handler, inputStream);
//                        doRead(transport, inputStream);
                    }
                    finally
                    {
                        if(_conn.closedForInput() && _conn.closedForOutput())
                        {
                            try
                            {
                                s.close();
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                    }
                }
            });

            inputThread.setDaemon(true);
            inputThread.start();

/*
            Thread outputThread = new Thread(new Runnable()
            {

                private int _lastWrite;

                public void run()
                {
                    try
                    {
//                        doRead(handler, inputStream);
                        final Object lock = new Object();
                        transport.setOutputStateChangeListener(new StateChangeListener()
                        {

                            public void onStateChange(final boolean active)
                            {
                                synchronized (lock)
                                {
                                    lock.notifyAll();
                                }
                            }
                        });

                        synchronized(lock)
                        {
                            while(transport.isOpenForOutput())
                            {
                                _lastWrite = 0;
                                transport.getNextBytes(new BytesProcessor()
                                {

                                    public void processBytes(final ByteBuffer buf)
                                    {
                                        _lastWrite = buf.remaining();
                                        try
                                        {
                                            outputStream.write(buf.array(),
                                                               buf.arrayOffset() + buf.position(),
                                                               buf.limit() - buf.position());
                                        }
                                        catch (IOException e)
                                        {
                                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                        }
                                    }
                                });
                                if(_lastWrite == 0 && transport.isOpenForOutput())
                                {
                                    try
                                    {
                                        lock.wait(1000);
                                    }
                                    catch (InterruptedException e)
                                    {
                                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                    }
                                }
                            }
                        }
                    }
                    finally
                    {
                        if(_conn.closedForInput() && _conn.closedForOutput())
                        {
                            try
                            {
                                s.close();
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                    }
                }
            });
*/

            _conn.open();

        }
        catch (IOException e)
        {
            throw new ConnectionException(e);
        }


    }



    private void doRead(final AMQPTransport transport, final InputStream inputStream)
    {
        byte[] buf = new byte[2<<15];
        ByteBuffer bbuf = ByteBuffer.wrap(buf);
        final Object lock = new Object();
        transport.setInputStateChangeListener(new StateChangeListener(){

            public void onStateChange(final boolean active)
            {
                synchronized(lock)
                {
                    lock.notifyAll();
                }
            }
        });

        try
        {
            int read;
            while((read = inputStream.read(buf)) != -1)
            {
                bbuf.position(0);
                bbuf.limit(read);

                while(bbuf.hasRemaining() && transport.isOpenForInput())
                {
                    transport.processBytes(bbuf);
                }


            }
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    public Session createSession()
    {
        Session session = new Session(this,String.valueOf(_sessionCount++));
        return session;
    }

    public ConnectionEndpoint getEndpoint()
    {
        return _conn;
    }

    private void doRead(final ConnectionHandler handler, final InputStream inputStream)
    {
        byte[] buf = new byte[2<<15];


        try
        {
            int read;
            boolean done = false;
            while(!done && (read = inputStream.read(buf)) != -1)
            {
                ByteBuffer bbuf = ByteBuffer.wrap(buf, 0, read);
                Binary b = new Binary(buf,0,read);

                if(RAW_LOGGER.isLoggable(Level.FINE))
                {
                    RAW_LOGGER.fine("RECV [" + _conn.getRemoteAddress() + "] : " + b.toString());
                }
                /*System.err.println(b);
                System.err.println("XXX: " + bbuf.hasRemaining() + "; " + handler.isDone());
                if(handler.isDone())
                {
                    System.err.println(handler.getClass().getName() + "IS DONE!");
                } */
                while(bbuf.hasRemaining() && !handler.isDone())
                {
                    handler.parse(bbuf);
                }


            }
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void close()
    {
        _conn.close();

        synchronized (_conn.getLock())
        {
            while(!_conn.closedForInput())
            {
                try
                {
                    _conn.getLock().wait();
                }
                catch (InterruptedException e)
                {

                }
            }
        }
    }


    public static class ConnectionException extends Exception
    {
        public ConnectionException(Throwable cause)
        {
            super(cause);
        }
    }


}
