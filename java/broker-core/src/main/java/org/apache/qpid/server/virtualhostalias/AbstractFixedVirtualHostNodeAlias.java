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
package org.apache.qpid.server.virtualhostalias;

import java.util.Map;

import org.apache.qpid.server.model.FixedVirtualHostNodeAlias;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHostNode;

abstract class AbstractFixedVirtualHostNodeAlias<X extends AbstractFixedVirtualHostNodeAlias<X>>
    extends AbstractVirtualHostAlias<X> implements FixedVirtualHostNodeAlias<X>
{
    @ManagedAttributeField
    private VirtualHostNode _virtualHostNode;

    protected AbstractFixedVirtualHostNodeAlias(final Map<String, Object> attributes,
                                                final Port port)
    {
        super(attributes, port);
    }


    @Override
    public VirtualHostNode<?> getVirtualHostNode()
    {
        return _virtualHostNode;
    }

    @Override
    public VirtualHostNode<?> getVirtualHostNode(final String name)
    {
        return matches(name) ? getVirtualHostNode() : null;
    }

    protected abstract boolean matches(final String name);
}
