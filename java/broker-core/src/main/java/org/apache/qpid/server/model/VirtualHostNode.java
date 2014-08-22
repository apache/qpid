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
package org.apache.qpid.server.model;

import java.util.Collection;

import org.apache.qpid.server.store.DurableConfigurationStore;

@ManagedObject(category=true, managesChildren=false)
public interface VirtualHostNode<X extends VirtualHostNode<X>> extends ConfiguredObject<X>
{
    String VIRTUALHOST_INITIAL_CONFIGURATION = "virtualHostInitialConfiguration";

    String VIRTUALHOST_BLUEPRINT_CONTEXT_VAR = "virtualhostBlueprint";

    @ManagedContextDefault(name = VIRTUALHOST_BLUEPRINT_CONTEXT_VAR)
    String DEFAULT_INITIAL_CONFIGURATION = "{}";

    @ManagedAttribute( defaultValue = "${" + VIRTUALHOST_BLUEPRINT_CONTEXT_VAR + "}")
    String getVirtualHostInitialConfiguration();

    VirtualHost<?,?,?> getVirtualHost();

    DurableConfigurationStore getConfigurationStore();

    @SuppressWarnings("rawtypes")
    Collection<? extends RemoteReplicationNode> getRemoteReplicationNodes();

    void stop();

    void start();
}
