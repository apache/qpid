/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.information.management;

import java.io.IOException;
import java.util.Collection;

import javax.management.JMException;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.management.common.mbeans.ServerInformation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.server.AMQBrokerManagerMBean;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

/** MBean class for the ServerInformationMBean. */
@MBeanDescription("Server Information Interface")
public class ServerInformationMBean extends AMQManagedObject implements ServerInformation
{
    private String buildVersion;
    private String productVersion;
    private ApplicationRegistry registry;
    
    public ServerInformationMBean(ApplicationRegistry applicationRegistry) throws JMException
    {
        super(ServerInformation.class, ServerInformation.TYPE);

        registry = applicationRegistry;
        buildVersion = QpidProperties.getBuildVersion();
        productVersion = QpidProperties.getReleaseVersion();
    }

    public String getObjectInstanceName()
    {
        return ServerInformation.TYPE;
    }
    
    public Integer getManagementApiMajorVersion() throws IOException
    {
        return QPID_JMX_API_MAJOR_VERSION;
    }

    public Integer getManagementApiMinorVersion() throws IOException
    {
        return QPID_JMX_API_MINOR_VERSION;
    }

    public String getBuildVersion() throws IOException
    {
        return buildVersion;
    }

    public String getProductVersion() throws IOException
    {
        return productVersion;
    }

    public void resetStatistics() throws Exception
    {
        registry.getDataStatistics().reset();
        registry.getMessageStatistics().reset();
        
        Collection<VirtualHost> virtualhosts = registry.getVirtualHostRegistry().getVirtualHosts();
        for (VirtualHost vhost : virtualhosts)
        {
            ((AMQBrokerManagerMBean) vhost.getBrokerMBean()).resetStatistics();
        }
    }

    public double getPeakMessageRate()
    {
        return registry.getMessageStatistics().getPeak();
    }

    public double getPeakDataRate()
    {
        return registry.getDataStatistics().getPeak();
    }

    public double getMessageRate()
    {
        return registry.getMessageStatistics().getRate();
    }

    public double getDataRate()
    {
        return registry.getDataStatistics().getRate();
    }

    public long getTotalMessages()
    {
        return registry.getMessageStatistics().getTotal();
    }

    public long getTotalData()
    {
        return registry.getDataStatistics().getTotal();
    }

    public boolean isStatisticsEnabled()
    {
        return registry.isStatisticsEnabled();
    }
}
