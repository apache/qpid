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
package org.apache.qpid.server.cluster.replay;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.server.cluster.MethodHandlerFactory;
import org.apache.qpid.server.cluster.MethodHandlerRegistry;
import org.apache.qpid.server.cluster.handler.WrappingMethodHandlerFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

import java.util.Arrays;

public class RecordingMethodHandlerFactory extends WrappingMethodHandlerFactory
{
    private final Iterable<FrameDescriptor> _frames = Arrays.asList(new FrameDescriptor[]
            {
                    new FrameDescriptor(QueueDeclareBody.class, new QueueDeclareBody()),
                    new FrameDescriptor(QueueDeleteBody.class, new QueueDeleteBody()),
                    new FrameDescriptor(QueueBindBody.class, new QueueBindBody()),
                    new FrameDescriptor(ExchangeDeclareBody.class, new ExchangeDeclareBody()),
                    new FrameDescriptor(ExchangeDeleteBody.class, new ExchangeDeleteBody()),
                    new FrameDescriptor(BasicConsumeBody.class, new BasicConsumeBody()),
                    new FrameDescriptor(BasicCancelBody.class, new BasicCancelBody())
            });


    public RecordingMethodHandlerFactory(MethodHandlerFactory factory, ReplayStore store)
    {
        super(factory, null, store);
    }

    protected boolean isWrappableState(AMQState state)
    {
        return AMQState.CONNECTION_OPEN.equals(state);
    }

    protected Iterable<FrameDescriptor> getWrappableFrameTypes(AMQState state)
    {
        return _frames;
    }
}
