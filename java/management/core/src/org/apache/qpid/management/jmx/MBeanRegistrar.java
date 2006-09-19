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
import org.apache.xmlbeans.XmlException;
import org.apache.qpid.AMQException;
import org.apache.qpid.management.ManagementConnection;
import org.apache.qpid.management.messaging.CMLMessageFactory;
import org.apache.qpid.schema.cml.CmlDocument;
import org.apache.qpid.schema.cml.FieldType;
import org.apache.qpid.schema.cml.InspectReplyType;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.OpenMBeanInfoSupport;
import java.util.Hashtable;

/**
 * Responsible for registering MBeans. This class will navigate through
 * our hierarchy of MBeans, registering them with the appropriate ObjectNames.
 *
 */
public class MBeanRegistrar
{
    private static final Logger _log = Logger.getLogger(MBeanRegistrar.class);

    /** The MBean server with which all MBeans will be registered. */
    private MBeanServer _targetMBeanServer;

    /** The connection used to communicate with the broker */
    private ManagementConnection _connection;

    private MBeanInfoRegistry _mbeanInfoRegistry;

    /**
     * Create a registrar for the specified MBean server
     * @param targetMBeanServer the MBean server with which all MBeans will be registered
     */
    public MBeanRegistrar(MBeanServer targetMBeanServer, ManagementConnection connection,
                          MBeanInfoRegistry mbeanInfoRegistry)
    {
        _targetMBeanServer = targetMBeanServer;
        _connection = connection;
        _mbeanInfoRegistry = mbeanInfoRegistry;
    }

    public void registerAllMBeans() throws AMQException, JMSException
    {
        registerMBean(null, 0);
    }

    /**
     * Asks the broker for details of a particular object id then creates and registers an
     * MBean with the MBeanServer.
     * @param objectId id of the object we want to inspect
     * @return the registered bean, from which the underlying inspect response can be retrieved if required
     * @throws AMQException
     * @throws JMSException
     */
    private CMLMBean registerMBean(CMLMBean parent, int objectId) throws AMQException, JMSException
    {
        TextMessage response = _connection.sendRequest(CMLMessageFactory.createInspectRequest(objectId));
        try
        {
            CmlDocument cmlDoc = CmlDocument.Factory.parse(response.getText());
            CmlDocument.Cml cml = cmlDoc.getCml();
            InspectReplyType inspect = cml.getInspectReply();
            if (_log.isDebugEnabled())
            {
                _log.debug("Inspect reply: " + inspect);
            }
            OpenMBeanInfoSupport mbeanInfo = _mbeanInfoRegistry.getOpenMBeanInfo(inspect.getClass1());
            AMQMBeanInfo extraMbeanInfo = _mbeanInfoRegistry.getAMQMBeanInfo(inspect.getClass1());
            CMLMBean mbean = new CMLMBean(parent, mbeanInfo, extraMbeanInfo, inspect, _connection, objectId);
            Hashtable<String, String> props = new Hashtable<String, String>();
            props.put("objectid", Integer.toString(objectId));
            props.put("type", mbean.getType());
            CMLMBean.populateAllTypeInfo(props, mbean);
            ObjectName mbeanObjectName = new ObjectName("org.apache.qpid", props);
            mbean.setObjectName(mbeanObjectName);
            _targetMBeanServer.registerMBean(mbean, mbeanObjectName);

            // recursively register all beans
            String nsDecl = "declare namespace cml='http://www.amqp.org/schema/cml';";
            for (MBeanAttributeInfo attributeInfo: mbeanInfo.getAttributes())
            {
                OpenMBeanAttributeInfo openAttributeInfo = (OpenMBeanAttributeInfo) attributeInfo;
                if (openAttributeInfo.getOpenType().equals(SimpleType.OBJECTNAME) &&
                    !"__parent".equals(openAttributeInfo.getName()))
                {
                    if (_log.isDebugEnabled())
                    {
                        _log.debug("Searching for fields with name: " + openAttributeInfo.getName());
                    }
                    FieldType[] fields = (FieldType[]) inspect.selectPath(nsDecl + "$this/cml:field[@name='" +
                                                                          openAttributeInfo.getName() + "']");
                    if (fields == null || fields.length == 0)
                    {
                        throw new AMQException("inspect xml did not contain field value for field " +
                                               attributeInfo.getName());
                    }
                    for (FieldType field : fields)
                    {
                        registerMBean(mbean, Integer.parseInt(field.getStringValue()));
                    }
                }
            }
            return mbean;
        }
        catch (XmlException e)
        {
            throw new AMQException(_log, "Error parsing broker response: " + e, e);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw new AMQException(_log, "Error registering MBean: " + e, e);
        }
    }
}
