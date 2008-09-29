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
package org.apache.qpid.management.domain.model;

import java.lang.management.ManagementFactory;
import java.util.UUID;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.qpid.management.Names;
import org.apache.qpid.management.domain.model.QpidClass.QpidManagedObject;
import org.apache.qpid.management.domain.model.type.Binary;
import org.apache.qpid.transport.util.Logger;

/**
 * A simple facade used to perform operations on Mbean server.
 * 
 * @author Andrea Gazzarini
 */
class JmxService
{
    private final static Logger LOGGER = Logger.get(JmxService.class);
    private MBeanServer _mxServer = ManagementFactory.getPlatformMBeanServer();
    
    
    /**
     * Registers a pre-existing object instance as an MBean with the MBean
     * server. 
     * 
     * @param instance the object instance.
     * @param brokerId the broker identifier.
     * @param packageName the name of the package containing this instance.
     * @param className the name of the owner class of this instance.
     * @param objectId the object instance identifier.
     */
    void registerObjectInstance(
            QpidManagedObject instance,
            UUID brokerId,
            String packageName, 
            String className, 
            Binary objectId)
    {    
            ObjectName name = createObjectName(brokerId, packageName, className, objectId);
            if (!_mxServer.isRegistered(name)) 
            {
                try
                {
                    _mxServer.registerMBean(instance, name);

                    LOGGER.debug(
                            "<QMAN-200026> : Object instance %s::%s::%s:%s successfully registered with MBean Server with name %s", 
                            brokerId,
                            packageName,
                            className,
                            objectId,
                            name);
                }  catch (Exception exception)
                {
                    throw new RuntimeException(exception);
                } 
            }
    }

    /**
     * Removes / unregisters a managed object instance from the MBean Server.
     * 
     * @param brokerId the broker identifier.
     * @param packageName the name of the package containing this instance.
     * @param className the name of the owner class of this instance.
     * @param objectId the object instance identifier.
     */
    void unregisterObjectInstance(
            UUID brokerId,
            String packageName, 
            String className, 
            Binary objectId)
    {    
            ObjectName name = createObjectName(brokerId, packageName, className, objectId);
            if (_mxServer.isRegistered(name)) 
            {
                try
                {
                    _mxServer.unregisterMBean(name);

                    LOGGER.debug(
                            "<QMAN-200012> : Object instance %s::%s::%s:%s successfully unregistered from MBean Server. " +
                            "Name was %s", 
                            brokerId,
                            packageName,
                            className,
                            objectId,
                            name);
                }  catch (Exception exception)
                {
                    LOGGER.error(exception,"<QMAN-100010> : Unable to unregister object instance %s.",name);
                } 
            }
    }    
    
    /**
     * Factory method for ObjectNames.
     * 
     * @param brokerId the broker identifier.
     * @param packageName the name of the package containing this instance.
     * @param className the name of the owner class of this instance.
     * @param objectId the object instance identifier.
     * @return the object name built according to the given parameters.
     */
    private ObjectName createObjectName(UUID brokerId, String packageName, String className, Binary objectId) 
    {
        String asString = new StringBuilder()
            .append(Names.DOMAIN_NAME)
            .append(':')
            .append(Names.BROKER_ID)
            .append('=')
            .append(brokerId)
            .append(',')
            .append(Names.PACKAGE)
            .append('=')
            .append(packageName)
            .append(',')
            .append(Names.CLASS)
            .append('=')
            .append(className)
            .append(',')
            .append(Names.OBJECT_ID)
            .append('=')
            .append(objectId)
            .toString();
        try
        {
            return new ObjectName(asString);
        } catch (MalformedObjectNameException exception)
        {
            throw new RuntimeException(exception);
        } 
    }
}