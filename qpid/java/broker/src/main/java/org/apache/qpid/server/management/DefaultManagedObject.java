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
package org.apache.qpid.server.management;

import javax.management.JMException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.log4j.Logger;
import org.apache.qpid.server.registry.ApplicationRegistry;

/**
 * Provides implementation of the boilerplate ManagedObject interface. Most managed objects should find it useful
 * to extend this class rather than implementing ManagedObject from scratch.
 *
 */
public abstract class DefaultManagedObject extends StandardMBean implements ManagedObject
{
    private static final Logger LOGGER = Logger.getLogger(DefaultManagedObject.class);
    
    private final Class<?> _managementInterface;

    private final String _typeName;

    private final MBeanInfo _mbeanInfo;

    private ManagedObjectRegistry _registry;

    protected DefaultManagedObject(Class<?> managementInterface, String typeName)
        throws NotCompliantMBeanException
    {
        super(managementInterface);
        _managementInterface = managementInterface;
        _typeName = typeName;
        _mbeanInfo = buildMBeanInfo();
    }

    @Override
    public MBeanInfo getMBeanInfo()
    {
        return _mbeanInfo;
    }

    public String getType()
    {
        return _typeName;
    }

    public Class<?> getManagementInterface()
    {
        return _managementInterface;
    }

    public ManagedObject getParentObject()
    {
        return null;
    }

    public void register() throws JMException
    {
        _registry = ApplicationRegistry.getInstance().getManagedObjectRegistry();
        _registry.registerObject(this);
    }

    public void unregister()
    {
        try
        {
            if(_registry != null)
            {
                _registry.unregisterObject(this);
            }
        }
        catch (JMException e)
        {
            LOGGER.error("Error unregistering managed object: " + this + ": " + e, e);
        }
        finally
        {
            _registry = null;
        }
    }

    public String toString()
    {
        return getObjectInstanceName() + "[" + getType() + "]";
    }

    /**
     * Created the ObjectName as per the JMX Specs
     * @return ObjectName
     * @throws MalformedObjectNameException
     */
    public ObjectName getObjectName() throws MalformedObjectNameException
    {
        String name = getObjectInstanceName();
        StringBuffer objectName = new StringBuffer(ManagedObject.DOMAIN);

        objectName.append(":type=");
        objectName.append(getHierarchicalType(this));

        objectName.append(",");
        objectName.append(getHierarchicalName(this));
        objectName.append("name=").append(name);

        return new ObjectName(objectName.toString());
    }

    protected ObjectName getObjectNameForSingleInstanceMBean() throws MalformedObjectNameException
    {
        StringBuffer objectName = new StringBuffer(ManagedObject.DOMAIN);

        objectName.append(":type=");
        objectName.append(getHierarchicalType(this));

        String hierarchyName = getHierarchicalName(this);
        if (hierarchyName != null)
        {
            objectName.append(",");
            objectName.append(hierarchyName.substring(0, hierarchyName.lastIndexOf(",")));
        }

        return new ObjectName(objectName.toString());
    }

    protected String getHierarchicalType(ManagedObject obj)
    {
        if (obj.getParentObject() != null)
        {
            String parentType = getHierarchicalType(obj.getParentObject()).toString();
            return parentType + "." + obj.getType();
        }
        else
            return obj.getType();
    }

    protected String getHierarchicalName(ManagedObject obj)
    {
        if (obj.getParentObject() != null)
        {
            String parentName = obj.getParentObject().getType() + "=" +
                                obj.getParentObject().getObjectInstanceName() + ","+
                                getHierarchicalName(obj.getParentObject());

            return parentName;
        }
        else
            return "";
    }

    private MBeanInfo buildMBeanInfo() throws NotCompliantMBeanException
    {
        return new MBeanInfo(this.getClass().getName(),
                      MBeanIntrospector.getMBeanDescription(this.getClass()),
                      MBeanIntrospector.getMBeanAttributesInfo(getManagementInterface()),
                      MBeanIntrospector.getMBeanConstructorsInfo(this.getClass()),
                      MBeanIntrospector.getMBeanOperationsInfo(getManagementInterface()),
                      this.getNotificationInfo());
    }

    public MBeanNotificationInfo[] getNotificationInfo()
    {
        return null;
    }
}
