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
 * A MethodDispatcher parses and dispatches a method segment.
 *
 * @author Rafael H. Schloming
 */

class MethodDispatcher<C extends DelegateResolver<C>>
    implements Handler<Event<C,Segment>>
{

    // XXX: should be passed in
    final private StructFactory factory = new StructFactory_v0_10();

    public void handle(Event<C,Segment> event)
    {
        System.out.println("got method segment:\n  " + event.target);
        ByteBuffer bb = event.target.getPayload();
        int type = bb.getInt();
        Struct struct = factory.create(type, new BBDecoder(bb));
        Delegate<C> delegate = event.context.resolve(struct);
        struct.delegate(event.context, delegate);
    }

}
