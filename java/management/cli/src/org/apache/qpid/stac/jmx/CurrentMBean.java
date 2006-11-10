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
package org.apache.qpid.stac.jmx;

import org.apache.qpid.AMQException;

import javax.management.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Stores information about the "current" MBean. This data is used when navigating the hierarchy.
 *
 * For example, we need to map between a name and an object id, and this stores that link.
 *
 */
public class CurrentMBean
{
    private MBeanServerConnection _mbeanServerConnection;

    public static final String PARENT_ATTRIBUTE = "__parent";

    private static final SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * Maps names to ObjectNames. Used for efficiency to avoid iterating through all names when doing a CD command.
     */
    private Map<String, ObjectName> _name2ObjectNameMap = new HashMap<String, ObjectName>();

    private ObjectName _mbeanObjectName;

    private MBeanInfo _mbeanInfo;

    public CurrentMBean(MBeanServerConnection mbeanServerConnection)
    {
        _mbeanServerConnection = mbeanServerConnection;
    }

    public void changeMBean(ObjectName objectName) throws AMQException
    {
        try
        {
            _mbeanInfo = _mbeanServerConnection.getMBeanInfo(objectName);
        }
        catch (Exception e)
        {
            throw new AMQException("Unable to look up MBean for object name " + objectName + ": " + e, e);
        }
        _mbeanObjectName = objectName;
    }

    public ObjectName getMBeanObjectName()
    {
        return _mbeanObjectName;
    }

    public MBeanInfo getMBeanInfo()
    {
        return _mbeanInfo;
    }

    public Object getAttributeValue(String name, String type) throws AMQException
    {
        // TODO: The type argument is a temporary workaround for a bug (somewhere!) in which
        // a date is returned as a String
        try
        {
            Object o = _mbeanServerConnection.getAttribute(_mbeanObjectName, name);
            if ("java.util.Date".equals(type))
            {

                return _dateFormat.format(new Date(Long.parseLong((String)o)));
            }
            else
            {
                return o;
            }
        }
        catch (Exception e)
        {
            throw new AMQException("Unable to read attribute value for attribute name " + name, e);
        }
    }

    /**
     * Get the objects (i.e. "directories") under the current mbean, ordered alphabetically. This method also
     * refreshes the cache that maps from name to ObjectName (this saves iterating through the attributes again).
     * @return a set containing the attribute infos, sorted by name
     */
    public SortedSet<MBeanAttributeInfo> getOrderedObjects() throws AMQException
    {
        TreeSet<MBeanAttributeInfo> attributes = new TreeSet<MBeanAttributeInfo>(new MBeanAttributeInfoComparator());
        _name2ObjectNameMap.clear();
        for (MBeanAttributeInfo ai : _mbeanInfo.getAttributes())
        {
            String type = ai.getType();

            if ("javax.management.ObjectName".equals(type))
            {
                _name2ObjectNameMap.put(ai.getName(), (ObjectName)getAttributeValue(ai.getName(), type));
                attributes.add(ai);
            }
        }
        return attributes;
    }

    public void refreshNameToObjectNameMap() throws AMQException
    {
        _name2ObjectNameMap.clear();
        for (MBeanAttributeInfo ai : _mbeanInfo.getAttributes())
        {
            final String type = ai.getType();

            if ("javax.management.ObjectName".equals(type))
            {
                _name2ObjectNameMap.put(ai.getName(), (ObjectName)getAttributeValue(ai.getName(), type));
            }
        }
    }

    /**
     * Gets an object name, given the "display name"
     * @param name the display name (usually returned to the user when he does an ls()
     * @return the object name
     */
    public ObjectName getObjectNameByName(String name)
    {
        return _name2ObjectNameMap.get(name);
    }

    public SortedSet<MBeanAttributeInfo> getOrderedAttributes()
    {
        TreeSet<MBeanAttributeInfo> attributes = new TreeSet<MBeanAttributeInfo>(new MBeanAttributeInfoComparator());
        for (MBeanAttributeInfo ai : _mbeanInfo.getAttributes())
        {
            String type = ai.getType();
            if (!"javax.management.ObjectName".equals(type))
            {
                attributes.add(ai);
            }
        }
        return attributes;
    }

    public SortedSet<MBeanOperationInfo> getOrderedOperations()
    {
        TreeSet<MBeanOperationInfo> operations = new TreeSet<MBeanOperationInfo>(new MBeanOperationInfoComparator());
        for (MBeanOperationInfo oi : _mbeanInfo.getOperations())
        {
            operations.add(oi);
        }
        return operations;
    }

    public void invoke(String methodName, Object... args) throws AMQException
    {
        try
        {
            _mbeanServerConnection.invoke(_mbeanObjectName, methodName, null, null);
        }
        catch (Exception e)
        {
            throw new AMQException("Error invoking method " + methodName + ": " + e, e);
        }
    }
}
