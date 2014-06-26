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
package org.apache.qpid.server.virtualhost.jdbc;

import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.jdbc.GenericJDBCMessageStore;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;

import java.util.Map;

@ManagedObject(category = false, type = JDBCVirtualHostImpl.VIRTUAL_HOST_TYPE)
public class JDBCVirtualHostImpl extends AbstractVirtualHost<JDBCVirtualHostImpl> implements JDBCVirtualHost<JDBCVirtualHostImpl>
{
    public static final String VIRTUAL_HOST_TYPE = "JDBC";

    @ManagedAttributeField
    private String _connectionUrl;

    @ManagedAttributeField
    private String _connectionPoolType;

    @ManagedObjectFactoryConstructor
    public JDBCVirtualHostImpl(final Map<String, Object> attributes,
                               final VirtualHostNode<?> virtualHostNode)
    {
        super(attributes, virtualHostNode);
    }

    @Override
    protected MessageStore createMessageStore()
    {
        return new GenericJDBCMessageStore();
    }

    @Override
    public String getConnectionUrl()
    {
        return _connectionUrl;
    }

    @Override
    public String getConnectionPoolType()
    {
        return _connectionPoolType;
    }
}
