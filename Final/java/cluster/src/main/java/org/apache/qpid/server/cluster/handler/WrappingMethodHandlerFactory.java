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
package org.apache.qpid.server.cluster.handler;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.cluster.MethodHandlerFactory;
import org.apache.qpid.server.cluster.MethodHandlerRegistry;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.StateAwareMethodListener;

public abstract class WrappingMethodHandlerFactory implements MethodHandlerFactory
{
    private final MethodHandlerFactory _delegate;
    private final StateAwareMethodListener _pre;
    private final StateAwareMethodListener _post;

    protected WrappingMethodHandlerFactory(MethodHandlerFactory delegate,
                                           StateAwareMethodListener pre,
                                           StateAwareMethodListener post)
    {
        _delegate = delegate;
        _pre = pre;
        _post = post;
    }

    public MethodHandlerRegistry register(AMQState state, MethodHandlerRegistry registry)
    {
        if (isWrappableState(state))
        {
            return wrap(_delegate.register(state, registry), state);
        }
        else
        {
            return _delegate.register(state, registry);
        }
    }

    protected abstract boolean isWrappableState(AMQState state);

    protected abstract Iterable<FrameDescriptor> getWrappableFrameTypes(AMQState state);

    private MethodHandlerRegistry wrap(MethodHandlerRegistry registry, AMQState state)
    {
        for (FrameDescriptor fd : getWrappableFrameTypes(state))
        {
            wrap(registry, fd.type, fd.instance);
        }
        return registry;
    }

    private <A extends AMQMethodBody, B extends Class<A>> void wrap(MethodHandlerRegistry r, B type, A frame)
    {
        r.addHandler(type, new WrappedListener<A>(r.getHandler(frame), _pre, _post));
    }

    protected static class FrameDescriptor<A extends AMQMethodBody, B extends Class<A>>
    {
        protected final A instance;
        protected final B type;

        public FrameDescriptor(B type, A instance)
        {
            this.instance = instance;
            this.type = type;
        }
    }
}
