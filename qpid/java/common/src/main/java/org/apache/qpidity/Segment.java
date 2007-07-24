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

import java.util.ArrayList;
import java.util.Collection;

/**
 * Segment
 *
 * @author Rafael H. Schloming
 */

class Segment
{

    private final Collection<Frame> frames = new ArrayList<Frame>();

    public void add(Frame frame)
    {
        frames.add(frame);
    }

    public ByteBuffer getPayload()
    {
        // we should probably use our own decoder interface here so
        // that we can directly read from the incoming frame objects
        // and automatically skip frame boundaries without copying
        // everything in order to get a contiguous byte buffer
        int capacity = 0;
        for (Frame frame : frames)
        {
            capacity += frame.getSize();
        }
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        for (Frame frame : frames)
        {
            buf.put(frame.getPayload());
        }
        buf.flip();
        return buf;
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        String sep = ",\n  ";

        for (Frame f : frames)
        {
            buf.append(f.toString());
            buf.append(sep);
        }

        buf.setLength(buf.length() - sep.length());

        return buf.toString();
    }

}
