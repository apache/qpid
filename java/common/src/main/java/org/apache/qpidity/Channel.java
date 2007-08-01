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

/**
 * Channel
 *
 * @author Rafael H. Schloming
 */

class Channel extends Invoker implements Handler<Frame>
{

    final private Connection connection;
    final private int channel;
    final private TrackSwitch<Channel> tracks;

    // session may be null
    private Session session;

    public Channel(Connection connection, int channel)
    {
        this.connection = connection;
        this.channel = channel;

        DelegateResolver<Channel> chDR =
            new SimpleDelegateResolver<Channel>(new ChannelDelegate());
        DelegateResolver<Session> ssnDR =
            new SimpleDelegateResolver<Session>(new SessionDelegate());

        tracks = new TrackSwitch<Channel>();
        tracks.map(Frame.L1, new MethodHandler<Channel>(getFactory(), chDR));
        tracks.map(Frame.L2, new MethodHandler<Channel>(getFactory(), chDR));
        tracks.map(Frame.L3, new SessionResolver<Frame>
                   (new MethodHandler<Session>(getFactory(), ssnDR)));
        tracks.map(Frame.L4, new SessionResolver<Frame>
                   (new ContentHandler<Session>(getFactory(), ssnDR)));
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

    public void write(Method m)
    {
        SizeEncoder sizer = new SizeEncoder();
        sizer.writeLong(m.getEncodedType());
        m.write(sizer);
        sizer.flush();
        int size = sizer.getSize();

        // XXX: need to set header flags properly
        SegmentEncoder enc = new SegmentEncoder(connection.getOutputHandler(),
                                                connection.getMaxFrame(),
                                                (byte) 0x0,
                                                m.getEncodedTrack(),
                                                m.getSegmentType(),
                                                channel,
                                                size);
        enc.writeLong(m.getEncodedType());
        m.write(enc);
        enc.flush();
    }

    protected StructFactory getFactory()
    {
        return connection.getFactory();
    }

    protected void invoke(Method m)
    {
        write(m);
    }

    protected void invoke(Method m, Handler<Struct> handler)
    {
        throw new UnsupportedOperationException();
    }

}
