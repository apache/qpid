
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
 */package org.apache.qpid.management.ui.jmx;

import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedServer;


public class ClientListener implements NotificationListener
{
    protected ManagedServer server = null;
    protected JMXServerRegistry serverRegistry = null;
    
    public ClientListener(ManagedServer server)
    {
        this.server = server;
        serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(server);
    }
    
    public void handleNotification(Notification notification, Object handback)
    {
        ObjectName objName = null;
        String     type = notification.getType();
        MBeanUtility.printOutput(type + ":" + objName);
        
        if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(type))
        {
            objName = ((MBeanServerNotification)notification).getMBeanName();
            getServerRegistry().registerManagedObject(objName);
        }
        else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(type))
        {
            objName = ((MBeanServerNotification)notification).getMBeanName();
            getServerRegistry().unregisterManagedObject(objName);
        }
        else if (JMXConnectionNotification.FAILED.equals(type))
        {
            ApplicationRegistry.serverConnectionClosed(server);
        }
    }

    protected JMXServerRegistry getServerRegistry()
    {
        if (serverRegistry == null)
            serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(server);
        
        return serverRegistry;
    }
    public ManagedServer getServer()
    {
        return server;
    }
}
