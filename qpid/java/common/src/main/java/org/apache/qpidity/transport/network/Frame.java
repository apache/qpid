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
package org.apache.qpidity.transport.network;

import org.apache.qpidity.transport.SegmentType;
import org.apache.qpidity.transport.util.SliceIterator;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import static org.apache.qpidity.transport.util.Functions.*;


/**
 * Frame
 *
 * @author Rafael H. Schloming
 */

// RA: changed it to public until we sort the package issues
public class Frame implements NetworkEvent, Iterable<ByteBuffer>
{
    public static final int HEADER_SIZE = 12;

    // XXX: enums?
    public static final byte L1 = 0;
    public static final byte L2 = 1;
    public static final byte L3 = 2;
    public static final byte L4 = 3;

    public static final byte RESERVED = 0x0;

    public static final byte VERSION = 0x0;

    public static final byte FIRST_SEG = 0x8;
    public static final byte LAST_SEG = 0x4;
    public static final byte FIRST_FRAME = 0x2;
    public static final byte LAST_FRAME = 0x1;

    final private byte flags;
    final private SegmentType type;
    final private byte track;
    final private int channel;
    final private List<ByteBuffer> fragments;
    private int size;

    public Frame(byte flags, SegmentType type, byte track, int channel)
    {
        this.flags = flags;
        this.type = type;
        this.track = track;
        this.channel = channel;
        this.size = 0;
        this.fragments = new ArrayList<ByteBuffer>();
    }

    public void addFragment(ByteBuffer fragment)
    {
        fragments.add(fragment);
        size += fragment.remaining();
    }

    public byte getFlags()
    {
        return flags;
    }

    public int getChannel()
    {
        return channel;
    }

    public int getSize()
    {
        return size;
    }

    public SegmentType getType()
    {
        return type;
    }

    public byte getTrack()
    {
        return track;
    }

    private boolean flag(byte mask)
    {
        return (flags & mask) != 0;
    }

    public boolean isFirstSegment()
    {
        return flag(FIRST_SEG);
    }

    public boolean isLastSegment()
    {
        return flag(LAST_SEG);
    }

    public boolean isFirstFrame()
    {
        return flag(FIRST_FRAME);
    }

    public boolean isLastFrame()
    {
        return flag(LAST_FRAME);
    }

    public Iterator<ByteBuffer> getFragments()
    {
        return new SliceIterator(fragments.iterator());
    }

    public Iterator<ByteBuffer> iterator()
    {
        return getFragments();
    }

    public void delegate(NetworkDelegate delegate)
    {
        delegate.frame(this);
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append(String.format
                   ("[%05d %05d %1d %s %d%d%d%d] ", getChannel(), getSize(),
                    getTrack(), getType(),
                    isFirstSegment() ? 1 : 0, isLastSegment() ? 1 : 0,
                    isFirstFrame() ? 1 : 0, isLastFrame() ? 1 : 0));

        boolean first = true;
        for (ByteBuffer buf : this)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.append(" | ");
            }

            str.append(str(buf));
        }

        return str.toString();
    }

}
