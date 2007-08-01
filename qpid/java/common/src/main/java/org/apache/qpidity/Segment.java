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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import java.nio.ByteBuffer;

import static org.apache.qpidity.Functions.*;


/**
 * Segment
 *
 * @author Rafael H. Schloming
 */

class Segment implements Iterable<ByteBuffer>
{

    private final Collection<ByteBuffer> fragments = new ArrayList<ByteBuffer>();

    public void add(ByteBuffer fragment)
    {
        fragments.add(fragment);
    }

    public Iterator<ByteBuffer> getFragments()
    {
        return new SliceIterator(fragments.iterator());
    }

    public Iterator<ByteBuffer> iterator()
    {
        return getFragments();
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        String sep = " | ";

        for (ByteBuffer buf : this)
        {
            str.append(str(buf));
            str.append(sep);
        }

        str.setLength(str.length() - sep.length());

        return str.toString();
    }

}
