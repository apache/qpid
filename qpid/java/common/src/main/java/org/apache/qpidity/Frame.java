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
import java.nio.ShortBuffer;

/**
 * Frame
 *
 * @author Rafael H. Schloming
 */

class Frame
{

    public static final short L1 = 0;
    public static final short L2 = 1;
    public static final short L3 = 2;
    public static final short L4 = 3;

    public static final short METHOD = 1;
    public static final short HEADER = 2;
    public static final short BODY = 3;

    final private short channel;
    final private short track;
    final private short type;
    final private boolean firstSegment;
    final private boolean lastSegment;
    final private boolean firstFrame;
    final private boolean lastFrame;
    final private ByteBuffer payload;

    // XXX
    final private int sequence = 0;

    public Frame(short channel, short track, short type, boolean firstSegment,
                 boolean lastSegment, boolean firstFrame, boolean lastFrame,
                 ByteBuffer payload)
    {
        this.channel = channel;
        this.track = track;
        this.type = type;
        this.firstSegment = firstSegment;
        this.lastSegment = lastSegment;
        this.firstFrame = firstFrame;
        this.lastFrame = lastFrame;
        this.payload = payload;
    }

    public short getChannel()
    {
        return channel;
    }

    public short getTrack()
    {
        return track;
    }

    public short getType()
    {
        return type;
    }

    public boolean isFirstSegment()
    {
        return firstSegment;
    }

    public boolean isLastSegment()
    {
        return lastSegment;
    }

    public boolean isFirstFrame()
    {
        return firstFrame;
    }

    public boolean isLastFrame()
    {
        return lastFrame;
    }

    public ByteBuffer getPayload()
    {
        return payload.slice();
    }

    public int getSize()
    {
        return payload.remaining();
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append(String.format
                   ("[%05d %05d %1d %1d %d%d%d%d]", channel, track, type,
                    getSize(),
                    firstSegment ? 1 : 0, lastSegment ? 1 : 0,
                    firstFrame ? 1 : 0, lastFrame ? 1 : 0));
        ShortBuffer shorts = payload.asShortBuffer();
        for (int i = 0; i < shorts.limit(); i++) {
            str.append(String.format(" %04x", shorts.get(i)));
            if (str.length() > 70) {
                str.append(" ...");
                break;
            }
        }

        return str.toString();
    }

}
