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


/**
 * SegmentAssembler is a stateful handler that aggregates Frame events
 * into Segment events. This should only be used where it is necessary
 * to assemble a Segment before processing, e.g. for Method and Header
 * segments.
 *
 * @author Rafael H. Schloming
 */

class SegmentAssembler<C> implements Handler<Event<C,Frame>>
{

    final private Handler<Event<C,Segment>> handler;
    private Segment segment;

    public SegmentAssembler(Handler<Event<C,Segment>> handler)
    {
        this.handler = handler;
    }

    public void handle(Event<C, Frame> event)
    {
        Frame frame = event.target;
        if (frame.isFirstFrame())
        {
            segment = new Segment();
        }

        for (ByteBuffer fragment : frame)
        {
            segment.add(fragment);
        }

        if (frame.isLastFrame())
        {
            handler.handle(new Event<C, Segment>(event.context, segment));
        }
    }

}
