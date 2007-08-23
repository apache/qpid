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
package org.apache.qpidity;

import java.nio.ByteBuffer;

import java.util.List;
import java.util.ArrayList;

import org.apache.qpidity.codec.SegmentEncoder;
import org.apache.qpidity.codec.SizeEncoder;

import static org.apache.qpidity.Frame.*;
import static org.apache.qpidity.Functions.*;


/**
 * Channel
 *
 * @author Rafael H. Schloming
 */

public class Channel extends Invoker implements Handler<Frame>
{

    final private Connection connection;
    final private int channel;
    final private TrackSwitch<Channel> tracks;
    final private Delegate<Channel> delegate;
    final private SessionDelegate sessionDelegate;
    // session may be null
    private Session session;

    private Method method = null;
    private List<ByteBuffer> data = null;
    private int dataSize;

    public Channel(Connection connection, int channel, SessionDelegate delegate)
    {
        this.connection = connection;
        this.channel = channel;
        this.delegate = new ChannelDelegate();
        this.sessionDelegate = delegate;

        tracks = new TrackSwitch<Channel>();
        tracks.map(L1, new MethodHandler<Channel>
                   (getMajor(), getMinor(), connection.getConnectionDelegate()));
        tracks.map(L2, new MethodHandler<Channel>
                   (getMajor(), getMinor(), this.delegate));
        tracks.map(L3, new SessionResolver<Frame>
                   (new MethodHandler<Session>
                    (getMajor(), getMinor(), delegate)));
        tracks.map(L4, new SessionResolver<Frame>
                   (new ContentHandler(getMajor(), getMinor(), delegate)));
    }

    public byte getMajor()
    {
        return connection.getMajor();
    }

    public byte getMinor()
    {
        return connection.getMinor();
    }

    public int getEncodedChannel() {
        return channel;
    }

    public Session getSession()
    {
        return session;
    }

    void setSession(Session session)
    {
        this.session = session;
    }

    public void handle(Frame frame)
    {
        tracks.handle(new Event<Channel,Frame>(this, frame));
    }

    private SegmentEncoder newEncoder(byte flags, byte track, byte type, int size)
    {
        return new SegmentEncoder(getMajor(),
                                  getMinor(),
                                  connection.getOutputHandler(),
                                  connection.getMaxFrame(),
                                  (byte) (flags | VERSION),
                                  track,
                                  type,
                                  channel,
                                  size);
    }

    public void method(Method m)
    {
        SizeEncoder sizer = new SizeEncoder(getMajor(), getMinor());
        sizer.writeLong(m.getEncodedType());
        m.write(sizer, getMajor(), getMinor());
        sizer.flush();
        int size = sizer.getSize();

        byte flags = FIRST_SEG;

        if (!m.hasPayload())
        {
            flags |= LAST_SEG;
        }

        SegmentEncoder enc = newEncoder(flags, m.getEncodedTrack(),
                                        m.getSegmentType(), size);
        enc.writeLong(m.getEncodedType());
        m.write(enc, getMajor(), getMinor());
        enc.flush();

        if (m.hasPayload())
        {
            method = m;
        }

        System.out.println("sent " + m);
    }

    public void headers(Struct ... headers)
    {
        if (method == null)
        {
            throw new IllegalStateException("cannot write headers without method");
        }

        SizeEncoder sizer = new SizeEncoder(getMajor(), getMinor());
        for (Struct hdr : headers)
        {
            sizer.writeLongStruct(hdr);
        }

        SegmentEncoder enc = newEncoder((byte) 0x0,
                                        method.getEncodedTrack(),
                                        HEADER,
                                        sizer.getSize());
        for (Struct hdr : headers)
        {
            enc.writeLongStruct(hdr);
            enc.flush();
            System.out.println("sent " + hdr);
        }
    }

    public void data(ByteBuffer buf)
    {
        if (data == null)
        {
            data = new ArrayList<ByteBuffer>();
            dataSize = 0;
        }
        data.add(buf);
        dataSize += buf.remaining();
    }

    public void data(String str)
    {
        data(str.getBytes());
    }

    public void data(byte[] bytes)
    {
        data(ByteBuffer.wrap(bytes));
    }

    public void end()
    {
        byte flags = LAST_SEG;
        SegmentEncoder enc = newEncoder(flags, method.getEncodedTrack(),
                                        BODY, dataSize);
        for (ByteBuffer buf : data)
        {
            enc.put(buf);
            System.out.println("sent " + str(buf));
        }
        enc.flush();
        data = null;
        dataSize = 0;
    }

    protected void invoke(Method m)
    {
        method(m);
    }

    protected <T> Future<T> invoke(Method m, Class<T> cls)
    {
        throw new UnsupportedOperationException();
    }

}
