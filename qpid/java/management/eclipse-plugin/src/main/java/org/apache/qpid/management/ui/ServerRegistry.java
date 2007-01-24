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
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.management.ui.jmx.ClientListener;
import org.apache.qpid.management.ui.model.ManagedAttributeModel;
import org.apache.qpid.management.ui.model.NotificationObject;
import org.apache.qpid.management.ui.model.OperationDataModel;

public abstract class ServerRegistry
{
    private ManagedServer _managedServer = null;
    // list of all Connection mbeans
    protected ConcurrentMap<String,List<ManagedBean>> _connections = new ConcurrentHashMap<String,List<ManagedBean>>();
    // list of all exchange mbeans
    protected ConcurrentMap<String,List<ManagedBean>> _exchanges = new ConcurrentHashMap<String,List<ManagedBean>>();
    // list of all queue mbenas
    protected ConcurrentMap<String,List<ManagedBean>> _queues = new ConcurrentHashMap<String,List<ManagedBean>>();
    
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
        String virtualHostName = mbean.getProperty("VirtualHost");
        _connections.putIfAbsent(virtualHostName, new ArrayList<ManagedBean>());
        List<ManagedBean> beans = _connections.get(virtualHostName);
        beans.add(mbean);
    }
    
    protected void addExchangeMBean(ManagedBean mbean)
    {
        String virtualHostName = mbean.getProperty("VirtualHost");
        _exchanges.putIfAbsent(virtualHostName, new ArrayList<ManagedBean>());
        List<ManagedBean> beans = _exchanges.get(virtualHostName);
        beans.add(mbean);
    }
    
    protected void addQueueMBean(ManagedBean mbean)
    {
        String virtualHostName = mbean.getProperty("VirtualHost");
        _queues.putIfAbsent(virtualHostName, new ArrayList<ManagedBean>());
        List<ManagedBean> beans = _queues.get(virtualHostName);
        beans.add(mbean);
    }
    
    protected void removeConnectionMBean(ManagedBean mbean)
    {
        String virtualHostName = mbean.getProperty("VirtualHost");
        _connections.putIfAbsent(virtualHostName, new ArrayList<ManagedBean>());
        List<ManagedBean> beans = _connections.get(virtualHostName);
        beans.remove(mbean);
    }
    
    protected void removeExchangeMBean(ManagedBean mbean)
    {
        String virtualHostName = mbean.getProperty("VirtualHost");
        _exchanges.putIfAbsent(virtualHostName, new ArrayList<ManagedBean>());
        List<ManagedBean> beans = _exchanges.get(virtualHostName);
        beans.remove(mbean);
    }
    
    protected void removeQueueMBean(ManagedBean mbean)
    {
        String virtualHostName = mbean.getProperty("VirtualHost");
        _queues.putIfAbsent(virtualHostName, new ArrayList<ManagedBean>());
        List<ManagedBean> beans = _queues.get(virtualHostName);
        beans.remove(mbean);
    }
    
    public List<ManagedBean> getConnections(String virtualHost)
    {
        _connections.putIfAbsent(virtualHost, new ArrayList<ManagedBean>());
        return _connections.get(virtualHost);
    }
    
    public List<ManagedBean> getExchanges(String virtualHost)
    {
        _exchanges.putIfAbsent(virtualHost, new ArrayList<ManagedBean>());
        return _exchanges.get(virtualHost);
    }
    
    public List<ManagedBean> getQueues(String virtualHost)
    {
        _queues.putIfAbsent(virtualHost, new ArrayList<ManagedBean>());
        return _queues.get(virtualHost);
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
    
    public abstract String[] getQueueNames(String virtualHost);
    
    public abstract String[] getExchangeNames(String virtualHost);
    
    public abstract String[] getConnectionNames(String virtualHost);
    
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
