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
package org.apache.qpid.server.configuration.startup;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.model.adapter.PortFactory;

public class PortRecoverer implements ConfiguredObjectRecoverer<Port>
{
    /**
     * delegates to a {@link PortFactory} so that the logic can be shared by
     * {@link BrokerAdapter}
     */
    private final PortFactory _portFactory;

    public PortRecoverer(PortFactory portFactory)
    {
        _portFactory = portFactory;
    }

    @Override
    public Port create(RecovererProvider recovererProvider, ConfigurationEntry configurationEntry, ConfiguredObject... parents)
    {
        Broker broker = RecovererHelper.verifyOnlyBrokerIsParent(parents);
        return _portFactory.createPort(configurationEntry.getId(), broker, configurationEntry.getAttributes());
    }

}
