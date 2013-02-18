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
package org.apache.qpid.server.protocol;

import java.io.PrintWriter;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.qpid.amqp_1_0.codec.FrameWriter;
import org.apache.qpid.amqp_1_0.codec.ProtocolHandler;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.framing.OversizeFrameException;
import org.apache.qpid.amqp_1_0.framing.SASLFrameHandler;
import org.apache.qpid.amqp_1_0.transport.SaslServerProvider;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.Container;
import org.apache.qpid.amqp_1_0.transport.FrameOutputHandler;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.v1_0.Connection_1_0;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;

public class ProtocolEngine_1_0_0_SASL implements ServerProtocolEngine, FrameOutputHandler
{
       private long _readBytes;
       private long _writtenBytes;

       private long _lastReadTime;
       private long _lastWriteTime;
       private final Broker _broker;
       private long _createTime = System.currentTimeMillis();
       private ConnectionEndpoint _conn;
       private long _connectionId;

       private static final ByteBuffer HEADER =
               ByteBuffer.wrap(new byte[]
                       {
                           (byte)'A',
                           (byte)'M',
                           (byte)'Q',
                           (byte)'P',
                           (byte) 3,
                           (byte) 1,
                           (byte) 0,
                           (byte) 0
                       });

        private static final ByteBuffer PROTOCOL_HEADER =
            ByteBuffer.wrap(new byte[]
                    {
                        (byte)'A',
                        (byte)'M',
                        (byte)'Q',
                        (byte)'P',
                        (byte) 0,
                        (byte) 1,
                        (byte) 0,
                        (byte) 0
                    });


       private FrameWriter _frameWriter;
       private ProtocolHandler _frameHandler;
       private ByteBuffer _buf = ByteBuffer.allocate(1024 * 1024);
       private Object _sendLock = new Object();
       private byte _major;
       private byte _minor;
       private byte _revision;
    private PrintWriter _out;
    private NetworkConnection _network;
    private Sender<ByteBuffer> _sender;


    static enum State {
           A,
           M,
           Q,
           P,
           PROTOCOL,
           MAJOR,
           MINOR,
           REVISION,
           FRAME
       }

       private State _state = State.A;


    public ProtocolEngine_1_0_0_SASL(final NetworkConnection networkDriver, final Broker broker,
                                     long id)
    {
        _connectionId = id;
        _broker = broker;
        if(networkDriver != null)
        {
            setNetworkConnection(networkDriver, networkDriver.getSender());
        }
    }


    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _network.getLocalAddress();
    }

    public long getReadBytes()
    {
        return _readBytes;
    }

    public long getWrittenBytes()
    {
        return _writtenBytes;
    }

    public void writerIdle()
    {
        //Todo
    }

    public void readerIdle()
    {
        //Todo
    }

    public void setNetworkConnection(final NetworkConnection network, final Sender<ByteBuffer> sender)
    {
        _network = network;
        _sender = sender;

        Container container = new Container(_broker.getId().toString());

        VirtualHost virtualHost = _broker.getVirtualHostRegistry().getVirtualHost((String)_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST));
        _conn = new ConnectionEndpoint(container, asSaslServerProvider(_broker.getSubjectCreator(getLocalAddress())));
        _conn.setRemoteAddress(getRemoteAddress());
        _conn.setConnectionEventListener(new Connection_1_0(virtualHost, _conn, _connectionId));
        _conn.setFrameOutputHandler(this);
        _conn.setSaslFrameOutput(this);

        _conn.setOnSaslComplete(new Runnable()
        {
            public void run()
            {
                if(_conn.isAuthenticated())
                {
                    _sender.send(PROTOCOL_HEADER.duplicate());
                    _sender.flush();
                }
                else
                {
                    _network.close();
                }
            }
        });
        _frameWriter =  new FrameWriter(_conn.getDescribedTypeRegistry());
        _frameHandler = new SASLFrameHandler(_conn);

        _sender.send(HEADER.duplicate());
        _sender.flush();

        _conn.initiateSASL();


    }

    private SaslServerProvider asSaslServerProvider(final SubjectCreator subjectCreator)
    {
        return new SaslServerProvider()
        {
            @Override
            public SaslServer getSaslServer(String mechanism, String fqdn) throws SaslException
            {
                return subjectCreator.createSaslServer(mechanism, fqdn, null);
            }
        };
    }

    public String getAddress()
    {
        return getRemoteAddress().toString();
    }

    public boolean isDurable()
    {
        return false;
    }

    private final Logger RAW_LOGGER = Logger.getLogger("RAW");


    public synchronized void received(ByteBuffer msg)
    {
        _lastReadTime = System.currentTimeMillis();
        if(RAW_LOGGER.isLoggable(Level.FINE))
        {
            ByteBuffer dup = msg.duplicate();
            byte[] data = new byte[dup.remaining()];
            dup.get(data);
            Binary bin = new Binary(data);
            RAW_LOGGER.fine("RECV[" + getRemoteAddress() + "] : " + bin.toString());
        }
         _readBytes += msg.remaining();
             switch(_state)
             {
                 case A:
                     if(msg.hasRemaining())
                     {
                         msg.get();
                     }
                     else
                     {
                         break;
                     }
                 case M:
                     if(msg.hasRemaining())
                     {
                         msg.get();
                     }
                     else
                     {
                         _state = State.M;
                         break;
                     }

                 case Q:
                     if(msg.hasRemaining())
                     {
                         msg.get();
                     }
                     else
                     {
                         _state = State.Q;
                         break;
                     }
                 case P:
                     if(msg.hasRemaining())
                     {
                         msg.get();
                     }
                     else
                     {
                         _state = State.P;
                         break;
                     }
                 case PROTOCOL:
                     if(msg.hasRemaining())
                     {
                         msg.get();
                     }
                     else
                     {
                         _state = State.PROTOCOL;
                         break;
                     }
                 case MAJOR:
                     if(msg.hasRemaining())
                     {
                         _major = msg.get();
                     }
                     else
                     {
                         _state = State.MAJOR;
                         break;
                     }
                 case MINOR:
                     if(msg.hasRemaining())
                     {
                         _minor = msg.get();
                     }
                     else
                     {
                         _state = State.MINOR;
                         break;
                     }
                 case REVISION:
                     if(msg.hasRemaining())
                     {
                         _revision = msg.get();

                         _state = State.FRAME;
                     }
                     else
                     {
                         _state = State.REVISION;
                         break;
                     }
                 case FRAME:
                     if(msg.hasRemaining())
                     {
                        _frameHandler = _frameHandler.parse(msg);
                     }
             }

     }

     public void exception(Throwable t)
     {
         t.printStackTrace();
     }

     public void closed()
     {
         // todo
        _conn.inputClosed();
        if(_conn != null && _conn.getConnectionEventListener() != null)
        {
            ((Connection_1_0)_conn.getConnectionEventListener()).closed();
        }

     }

     public long getCreateTime()
     {
         return _createTime;
     }


     public boolean canSend()
     {
         return true;
     }

     public void send(final AMQFrame amqFrame)
     {
         send(amqFrame, null);
     }

     private static final Logger FRAME_LOGGER = Logger.getLogger("FRM");


     public void send(final AMQFrame amqFrame, ByteBuffer buf)
     {

         synchronized(_sendLock)
         {
             _lastWriteTime = System.currentTimeMillis();
             if(FRAME_LOGGER.isLoggable(Level.FINE))
             {
                 FRAME_LOGGER.fine("SEND[" + getRemoteAddress() + "|" + amqFrame.getChannel() + "] : " + amqFrame.getFrameBody());
             }

             _frameWriter.setValue(amqFrame);

             ByteBuffer dup = ByteBuffer.allocate(_conn.getMaxFrameSize());

             int size = _frameWriter.writeToBuffer(dup);
             if(size > _conn.getMaxFrameSize())
             {
                 throw new OversizeFrameException(amqFrame,size);
             }

             dup.flip();
             _writtenBytes += dup.limit();

             if(RAW_LOGGER.isLoggable(Level.FINE))
              {
                 ByteBuffer dup2 = dup.duplicate();
                 byte[] data = new byte[dup2.remaining()];
                 dup2.get(data);
                 Binary bin = new Binary(data);
                 RAW_LOGGER.fine("SEND[" + getRemoteAddress() + "] : " + bin.toString());
              }


             _sender.send(dup);
             _sender.flush();


         }
     }

     public void send(short channel, FrameBody body)
     {
         AMQFrame frame = AMQFrame.createAMQFrame(channel, body);
         send(frame);

     }

     public void close()
     {
         _sender.close();
     }

     public void setLogOutput(final PrintWriter out)
     {
         _out = out;
     }

     public long getConnectionId()
     {
         return _connectionId;
     }

    public long getLastReadTime()
    {
        return _lastReadTime;
    }

    public long getLastWriteTime()
    {
        return _lastWriteTime;
    }
}
