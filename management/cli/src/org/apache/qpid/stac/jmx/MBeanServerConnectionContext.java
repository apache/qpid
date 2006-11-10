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
import org.apache.qpid.management.jmx.JmxConstants;
import org.apache.qpid.stac.commands.CdCommand;
import org.apache.qpid.stac.commands.LsCommand;
import org.apache.log4j.Logger;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.Set;

public class MBeanServerConnectionContext
{
    private static final Logger _log = Logger.getLogger(MBeanServerConnectionContext.class);

    /**
     * The connection to the MBean server. Can be remote or local, depending on whether we are proxying.
     */
    private MBeanServerConnection _connection;

    /**
     * The connector used to make the connection to the remote MBean server
     */
    private JMXConnector _connector;

    private CurrentMBean _currentMBean;

    /*
    * Initialize connection to the Domain Runtime MBean Server
    */
    public void connect(String host) throws AMQException
    {
        if (host == null)
        {
            _connection = (MBeanServerConnection) ManagementFactory.getPlatformMBeanServer();
        }
        else
        {
            String serviceURLString  = "service:jmx:local://localhost";

            try
            {
                JMXServiceURL serviceURL = new JMXServiceURL(serviceURLString);
                _connector = JMXConnectorFactory.connect(serviceURL, null);
                _connection = _connector.getMBeanServerConnection();
            }
            catch (Exception e)
            {
                throw new AMQException("Unable to connect to remote MBean server with service url " + serviceURLString +
                                       ": " + e, e);
            }
        }
        _currentMBean = new CurrentMBean(_connection);
        changeBean(getRootObjectName());
    }

    /**
     * Connect to the local MBean server
     * @throws AMQException
     */
    public void connect() throws AMQException
    {
        connect(null);
    }

    public void disconnect() throws AMQException
    {
        if (_connection != null)
        {
            try
            {
                ObjectName queryName = new ObjectName(JmxConstants.JMX_DOMAIN + ":*");
                Set<ObjectInstance> beans = _connection.queryMBeans(queryName, null);
                for (ObjectInstance bean : beans)
                {
                    _log.debug("Unregistering MBean: " + bean.getObjectName());
                    _connection.unregisterMBean(bean.getObjectName());
                }
            }
            catch (Exception e)
            {
                throw new AMQException("Error unregistering MBeans: " + e, e);
            }
        }
    }

    public ObjectName getRootObjectName() throws AMQException
    {
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put("objectid", "0");
        props.put("type", "broker");
        try
        {
            return new ObjectName(JmxConstants.JMX_DOMAIN, props);
        }
        catch (MalformedObjectNameException e)
        {
            throw new AMQException("Cannot construct root MBean object name: " + e, e);
        }
    }

    private void changeBean(ObjectName objectName) throws AMQException
    {
        _currentMBean.changeMBean(objectName);
    }

    /**
     * Change the current bean to the one underneath the current bean, represented by the display name
     * @param name
     * @throws AMQException
     */
    public void changeBean(String name) throws AMQException
    {
        checkConnection();
        if (name.equals("/"))
        {
            changeBean(getRootObjectName());
        }
        else
        {
            ObjectName objName = _currentMBean.getObjectNameByName(name);
            if (CurrentMBean.PARENT_ATTRIBUTE.equals(name) && objName == null)
            {
                // we have tried to change up a level from the root, so just ignore
                return;
            }
            if (objName == null)
            {
                // could be stale cache, so refresh
                _currentMBean.refreshNameToObjectNameMap();
                objName = _currentMBean.getObjectNameByName(name);
            }
            if (objName == null)
            {
                throw new AMQException("Unknown managed object with name: " + name);
            }
            else
            {
                changeBean(objName);
            }
        }
    }

    public void ls() throws AMQException
    {
        checkConnection();
        LsCommand.execute(this);
    }

    public void cd(String destination) throws AMQException
    {
        CdCommand.execute(this, destination);
    }

    public void invoke(String methodName, Object... args) throws AMQException
    {
        _currentMBean.invoke(methodName, args);
    }

    public CurrentMBean getCurrentMBean()
    {
        return _currentMBean;
    }

    public MBeanServerConnection getMBeanServerConnection()
    {
        return _connection;
    }

    private void checkConnection() throws NotConnectedException
    {
        if (_connection == null)
        {
            throw new NotConnectedException();
        }
    }
}
