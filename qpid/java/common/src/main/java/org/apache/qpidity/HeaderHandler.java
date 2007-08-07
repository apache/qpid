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
import java.util.Iterator;


/**
 * HeaderHandler
 *
 * @author Rafael H. Schloming
 */

class HeaderHandler implements Handler<Event<Session,Segment>>
{

    private static final Struct[] EMPTY_STRUCT_ARRAY = {};

    private final byte major;
    private final byte minor;
    private final SessionDelegate delegate;

    public HeaderHandler(byte major, byte minor, SessionDelegate delegate)
    {
        this.major = major;
        this.minor = minor;
        this.delegate = delegate;
    }

    public void handle(Event<Session,Segment> event)
    {
        System.out.println("got header segment:\n  " + event.target);
        Iterator<ByteBuffer> fragments = event.target.getFragments();
        FragmentDecoder dec = new FragmentDecoder(major, minor, fragments);
        ArrayList<Struct> headers = new ArrayList();
        while (dec.hasRemaining())
        {
            headers.add(dec.readLongStruct());
        }
        delegate.headers(event.context, headers.toArray(EMPTY_STRUCT_ARRAY));
    }

}
