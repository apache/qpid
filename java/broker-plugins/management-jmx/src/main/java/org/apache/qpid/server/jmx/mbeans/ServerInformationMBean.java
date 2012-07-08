/*
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
package org.apache.qpid.server.jmx.mbeans;

import java.io.IOException;

import javax.management.JMException;
import javax.management.NotCompliantMBeanException;

import org.apache.qpid.management.common.mbeans.ServerInformation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Broker;

@MBeanDescription("Server Information Interface")
public class ServerInformationMBean extends AbstractStatisticsGatheringMBean<Broker> implements ServerInformation
{
    private final Broker _broker;

    public ServerInformationMBean(ManagedObjectRegistry registry, Broker broker) 
                                                    throws NotCompliantMBeanException, JMException
    {
        super(ServerInformation.class, ServerInformation.TYPE, registry, broker);
        _broker = broker;

        register();
    }

    @Override
    public String getObjectInstanceName()
    {
        return ServerInformation.TYPE;
    }

    @Override
    public Integer getManagementApiMajorVersion() throws IOException
    {
        return QPID_JMX_API_MAJOR_VERSION;
    }

    @Override
    public Integer getManagementApiMinorVersion() throws IOException
    {
        return QPID_JMX_API_MINOR_VERSION;
    }

    @Override
    public String getBuildVersion() throws IOException
    {
        return (String) _broker.getAttribute(Broker.BUILD_VERSION);
    }

    @Override
    public String getProductVersion() throws IOException
    {
        return (String) _broker.getAttribute(Broker.PRODUCT_VERSION);
    }

    @Override
    public boolean isStatisticsEnabled()
    {
        return true;
    }

    @Override
    public ManagedObject getParentObject()
    {
        // does not have a parent
        return null;
    }
}
