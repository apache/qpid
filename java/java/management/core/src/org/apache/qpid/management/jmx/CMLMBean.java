/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.management.jmx;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.management.ManagementConnection;
import org.apache.qpid.management.messaging.CMLMessageFactory;
import org.apache.qpid.schema.cml.CmlDocument;
import org.apache.qpid.schema.cml.FieldType;
import org.apache.qpid.schema.cml.InspectReplyType;
import org.apache.qpid.schema.cml.MethodReplyType;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.management.*;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanInfoSupport;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import java.util.Hashtable;

public class CMLMBean implements DynamicMBean
{
    private static final Logger _log = Logger.getLogger(CMLMBean.class);

    /**
     * The number of milliseconds after which data values are considered "stale" and will be
     * refreshed by querying the broker. This is a way of ensure that reading attributes
     * repeatedly does not hit the broker heavily.
     */
    private static final long REFRESH_IN_MILLIS = 2000;

    /**
     * Name of the attribute for the parent MBean
     */
    public static final String PARENT_ATTRIBUTE = "__parent";

    private OpenMBeanInfoSupport _mbeanInfo;

    private AMQMBeanInfo _extraMbeanInfo;

    /**
     * The cached inspect reply. This is used to read attribute values and is refreshed automatically
     * if a request for an attribute is made after the time interval specified in REFRESH_IN_MILLIS
     */
    private InspectReplyType _inspectReply;

    private CMLMBean _parent;

    private ObjectName _objectName;

    private ManagementConnection _connection;

    private int _objectId;

    private long _lastRefreshTime = System.currentTimeMillis();

    public CMLMBean(CMLMBean parent, OpenMBeanInfoSupport mbeanInfo, AMQMBeanInfo extraMbeanInfo,
                    InspectReplyType inspectReply, ManagementConnection connection, int objectId)
    {
        _mbeanInfo = mbeanInfo;
        _extraMbeanInfo = extraMbeanInfo;
        _inspectReply = inspectReply;
        _parent = parent;
        _connection = connection;
        _objectId = objectId;
    }

    /**
     * Utility method that populates all the type infos up to the root. Used when
     * constructing the ObjectName.
     * We end up with properties of the form "className", "objectId" in the map.
     * @param leaf the child node. Must not be null. Note that the child types are not populated since the
     * convention is different for the child where instead of "className" the word "type" is
     * used. See the JMX Best Practices document on the Sun JMX website for details.
     * @param properties
     */
    public static void populateAllTypeInfo(Hashtable<String, String> properties, CMLMBean leaf)
    {
        CMLMBean current = leaf.getParent();
        while (current != null)
        {
            properties.put(current.getType(), Integer.toString(current.getObjectId()));
            current = current.getParent();
        }
    }

    public String getType()
    {
        return _inspectReply.getClass1();
    }

    public int getObjectId()
    {
        return _inspectReply.getObject2();
    }

    public InspectReplyType getInspectReply()
    {
        return _inspectReply;
    }

    public CMLMBean getParent()
    {
        return _parent;
    }

    public ObjectName getObjectName()
    {
        return _objectName;
    }

    public void setObjectName(ObjectName objectName)
    {
        _objectName = objectName;
    }

    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        if (PARENT_ATTRIBUTE.equals(attribute))
        {
            if (_parent == null)
            {
                return null;
            }
            else
            {
                return _parent.getObjectName();
            }
        }
        if (needRefresh())
        {            
            refreshValues();
        }
        String nsDecl = "declare namespace cml='http://www.amqp.org/schema/cml';";
        FieldType[] fields = (FieldType[]) _inspectReply.selectPath(nsDecl + "$this/cml:field[@name='" +
                                                                    attribute + "']");
        if (fields == null || fields.length == 0)
        {
            throw new AttributeNotFoundException("Attribute " + attribute + " not found");
        }
        else
        {
            OpenMBeanAttributeInfo attrInfo = _extraMbeanInfo.getAttributeInfo(attribute);
            OpenType openType = attrInfo.getOpenType();
            String value = fields[0].getStringValue();
            try
            {
                return createAttributeValue(openType, value, attrInfo.getName());
            }
            catch (MalformedObjectNameException e)
            {
                throw new MBeanException(e);
            }
        }
    }

    private boolean needRefresh()
    {
        return ((System.currentTimeMillis() - _lastRefreshTime) > REFRESH_IN_MILLIS);
    }

    private void refreshValues() throws MBeanException
    {
        _log.debug("Refreshing values...");
        try
        {
            TextMessage response = _connection.sendRequest(CMLMessageFactory.createInspectRequest(_objectId));

            CmlDocument cmlDoc = CmlDocument.Factory.parse(response.getText());
            _inspectReply = cmlDoc.getCml().getInspectReply();
            _lastRefreshTime = System.currentTimeMillis();
        }
        catch (Exception e)
        {
            throw new MBeanException(e);
        }
    }

    private Object createAttributeValue(OpenType openType, String value, String mbeanType)
            throws MalformedObjectNameException
    {
        if (openType.equals(SimpleType.STRING))
        {
            return value;
        }
        else if (openType.equals(SimpleType.BOOLEAN))
        {
            return Boolean.valueOf(value);
        }
        else if (openType.equals(SimpleType.INTEGER))
        {
            return Integer.valueOf(value);
        }
        else if (openType.equals(SimpleType.DOUBLE))
        {
            return Double.valueOf(value);
        }
        else if (openType.equals(SimpleType.OBJECTNAME))
        {
            Hashtable<String, String> props = new Hashtable<String, String>();
            props.put("objectid", value);
            props.put("type", mbeanType);
            // this populates all type info for parents
            populateAllTypeInfo(props, this);
            // add in type info for this level. This information is available from the inspect reply xml fragment
            props.put(_inspectReply.getClass1(), Integer.toString(_inspectReply.getObject2()));
            return new ObjectName(JmxConstants.JMX_DOMAIN, props);
        }
        else
        {
            _log.warn("Unsupported open type: " + openType + " - returning string value");
            return value;
        }
    }

    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException,
                                                         MBeanException, ReflectionException
    {

    }

    public AttributeList getAttributes(String[] attributes)
    {
        AttributeList al = new AttributeList(attributes.length);
        for (String name : attributes)
        {
            try
            {
                Object value = getAttribute(name);
                final Attribute attr = new Attribute(name, value);
                al.add(attr);
            }
            catch (Exception e)
            {
                _log.error("Unable to get value for attribute: " + name, e);
            }
        }
        return al;
    }

    public AttributeList setAttributes(AttributeList attributes)
    {
        return null;
    }

    public Object invoke(String actionName, Object params[], String signature[]) throws MBeanException,
                                                                                        ReflectionException
    {
        _log.debug("Invoke called on action " + actionName);
        try
        {
            TextMessage response = _connection.sendRequest(CMLMessageFactory.createMethodRequest(_objectId, actionName));
            CmlDocument cmlDoc = CmlDocument.Factory.parse(response.getText());
            CmlDocument.Cml cml = cmlDoc.getCml();
            MethodReplyType methodReply = cml.getMethodReply();
            if (methodReply.getStatus() != MethodReplyType.Status.OK)
            {
                throw new MBeanException(new Exception("Response code from method: " + methodReply.getStatus()));
            }
            return null;
        }
        catch (AMQException e)
        {
            throw new MBeanException(e);
        }
        catch (JMSException e)
        {
            throw new MBeanException(e);
        }
        catch (org.apache.xmlbeans.XmlException e)
        {
            throw new MBeanException(e);
        }
    }

    public MBeanInfo getMBeanInfo()
    {
        return _mbeanInfo;
    }
}
