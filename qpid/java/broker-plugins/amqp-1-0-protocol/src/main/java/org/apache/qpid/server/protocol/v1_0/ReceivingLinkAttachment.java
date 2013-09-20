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
package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Source;
import org.apache.qpid.amqp_1_0.type.Target;

public class ReceivingLinkAttachment
{
    private final Session_1_0         _session;
    private final ReceivingLinkEndpoint _endpoint;

    public ReceivingLinkAttachment(final Session_1_0 session, final ReceivingLinkEndpoint endpoint)
    {
        _session = session;
        _endpoint = endpoint;
    }

    public Session_1_0 getSession()
    {
        return _session;
    }

    public ReceivingLinkEndpoint getEndpoint()
    {
        return _endpoint;
    }

    public Source getSource()
    {
        return getEndpoint().getSource();
    }

    public void setDeliveryStateHandler(final DeliveryStateHandler handler)
    {
        getEndpoint().setDeliveryStateHandler(handler);
    }

    public void updateDisposition(final Binary deliveryTag, final DeliveryState state, final boolean settled)
    {
        getEndpoint().updateDisposition(deliveryTag, state, settled);
    }

    public Target getTarget()
    {
        return getEndpoint().getTarget();
    }
}
