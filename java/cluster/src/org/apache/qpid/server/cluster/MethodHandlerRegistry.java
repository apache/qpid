/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.cluster;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.state.StateAwareMethodListener;

import java.util.HashMap;
import java.util.Map;

public class MethodHandlerRegistry
{
    private final Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>> registry =
            new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();

    public <A extends AMQMethodBody, B extends Class<A>> MethodHandlerRegistry addHandler(B type, StateAwareMethodListener<A> handler)
    {
        registry.put(type, handler);
        return this;
    }

    public <B extends AMQMethodBody> StateAwareMethodListener<B> getHandler(B frame)
    {
        return (StateAwareMethodListener<B>) registry.get(frame.getClass());
    }
}
