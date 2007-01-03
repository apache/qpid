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
package org.apache.qpid.management.ui;


import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.management.ui.jmx.ClientListener;
import org.apache.qpid.management.ui.model.ManagedAttributeModel;
import org.apache.qpid.management.ui.model.NotificationObject;
import org.apache.qpid.management.ui.model.OperationDataModel;

public abstract class ServerRegistry
{
    private ManagedServer _managedServer = null;
    // list of all Connection mbeans
    protected List<ManagedBean> _connections = new ArrayList<ManagedBean>();
    // list of all exchange mbeans
    protected List<ManagedBean> _exchanges = new ArrayList<ManagedBean>();
    // list of all queue mbenas
    protected List<ManagedBean> _queues = new ArrayList<ManagedBean>();
    
    public ServerRegistry()
    {
        
    }
    
    public ServerRegistry(ManagedServer server)
    {
        _managedServer = server;
    }
    
    public ManagedServer getManagedServer()
    {
        return _managedServer;
    }

    public void setManagedServer(ManagedServer server)
    {
        _managedServer = server;
    }
    
    protected void addConnectionMBean(ManagedBean mbean)
    {
        _connections.add(mbean);
    }
    
    protected void addExchangeMBean(ManagedBean mbean)
    {
        _exchanges.add(mbean);
    }
    
    protected void addQueueMBean(ManagedBean mbean)
    {
        _queues.add(mbean);
    }
    
    protected void removeConnectionMBean(ManagedBean mbean)
    {
        _connections.remove(mbean);
    }
    
    protected void removeExchangeMBean(ManagedBean mbean)
    {
        _exchanges.remove(mbean);
    }
    
    protected void removeQueueMBean(ManagedBean mbean)
    {
        _queues.remove(mbean);
    }
    
    public List<ManagedBean> getConnections()
    {
        return _connections;
    }
    
    public List<ManagedBean> getExchanges()
    {
        return _exchanges;
    }
    
    public List<ManagedBean> getQueues()
    {
        return _queues;
    }
    
    public abstract void addManagedObject(ManagedBean key);
    
    public abstract List<ManagedBean> getMBeans();
    
    public abstract void removeManagedObject(ManagedBean mbean);
   
    public List<ManagedBean> getObjectsToBeRemoved()
    {
        return null;
    }
    
    public abstract ManagedAttributeModel getAttributeModel(ManagedBean mbean);
    
    public abstract Object getServerConnection();
    
    public abstract void closeServerConnection() throws Exception;
    
    public abstract OperationDataModel getOperationModel(ManagedBean mbean);
    
    public abstract String[] getQueueNames();
    
    public abstract String[] getExchangeNames();
    
    public abstract String[] getConnectionNames();
    
    public abstract List<NotificationObject> getNotifications(ManagedBean mbean);
    
    public abstract boolean hasSubscribedForNotifications(ManagedBean mbean, String name, String type);
    
    public abstract void clearNotifications(ManagedBean mbean);
    
    public ClientListener getNotificationListener()
    {
        return null;
    }

    public ClientListener getClientListener()
    {
        return null;
    }
}
