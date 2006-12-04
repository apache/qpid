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
package org.apache.qpid.management.ui.jmx;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ManagedServer;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.model.ManagedAttributeModel;
import org.apache.qpid.management.ui.model.NotificationInfoModel;
import org.apache.qpid.management.ui.model.NotificationObject;
import org.apache.qpid.management.ui.model.OperationDataModel;


public class JMXServerRegistry extends ServerRegistry
{
    private ObjectName _serverObjectName = null;
    private JMXConnector _jmxc = null;
    private MBeanServerConnection _mbsc = null;
    
    private List<ManagedBean> _mbeansToBeAdded   = new ArrayList<ManagedBean>();
    private List<ManagedBean> _mbeansToBeRemoved = new ArrayList<ManagedBean>();
    
    private List<String> _queues    = new ArrayList<String>();
    private List<String> _exchanges = new ArrayList<String>();
    
    private HashMap<String, ManagedBean>   _mbeansMap    = new HashMap<String, ManagedBean>(); 
    private HashMap<String, MBeanInfo>     _mbeanInfoMap = new HashMap<String, MBeanInfo>();
    private HashMap<String, ManagedAttributeModel>    _attributeModelMap = new HashMap<String, ManagedAttributeModel>();
    private HashMap<String, OperationDataModel>       _operationModelMap = new HashMap<String, OperationDataModel>();
    private HashMap<String, List<NotificationInfoModel>> _notificationInfoMap = new HashMap<String, List<NotificationInfoModel>>();
    private HashMap<String, List<NotificationObject>> _notificationsMap  = new HashMap<String, List<NotificationObject>>();
    private HashMap<String, HashMap<String, List<String>>> _subscribedNotificationMap = new HashMap<String, HashMap<String, List<String>>>();
    
    private ClientNotificationListener _notificationListener = null;
    private ClientListener _clientListener = null;
    
    public JMXServerRegistry(ManagedServer server) throws Exception
    {
        super(server);
        JMXServiceURL jmxUrl = new JMXServiceURL(server.getUrl());
        _jmxc = JMXConnectorFactory.connect(jmxUrl, null);
        _mbsc = _jmxc.getMBeanServerConnection();
        
        _clientListener = new ClientListener(server);
        _notificationListener = new ClientNotificationListener(server);
        
        _jmxc.addConnectionNotificationListener(_clientListener, null, null);       
        _serverObjectName = new ObjectName("JMImplementation:type=MBeanServerDelegate");
        _mbsc.addNotificationListener(_serverObjectName, _clientListener, null, null);
    }
    
    public MBeanServerConnection getServerConnection()
    {
        return _mbsc;
    }
    
    /**
     * removes all listeners from the mbean server. This is required when user
     * disconnects the Qpid server connection
     */
    public void closeServerConnection() throws Exception
    {
        if (_jmxc != null)
            _jmxc.removeConnectionNotificationListener(_clientListener);
        
        if (_mbsc != null)
            _mbsc.removeNotificationListener(_serverObjectName, _clientListener);
        
        // remove mbean notification listeners
        for (String mbeanName : _subscribedNotificationMap.keySet())
        {
            _mbsc.removeNotificationListener(new ObjectName(mbeanName), _notificationListener);
        }
    }
    
    public ManagedBean getManagedObject(String uniqueName)
    {
        return _mbeansMap.get(uniqueName);
    }
    
    public void addManagedObject(ManagedBean key)
    {
        if (Constants.QUEUE.equals(key.getType()))
            _queues.add(key.getName());
        else if (Constants.EXCHANGE.equals(key.getType()))
            _exchanges.add(key.getName());
        
        _mbeansMap.put(key.getUniqueName(), key);
    }

    public void removeManagedObject(ManagedBean mbean)
    {
        if (Constants.QUEUE.equals(mbean.getType()))
            _queues.remove(mbean.getName());
        else if (Constants.EXCHANGE.equals(mbean.getType()))
            _exchanges.remove(mbean.getName());
        
        _mbeansMap.remove(mbean.getUniqueName());
    }
    
    public void putMBeanInfo(ManagedBean mbean, MBeanInfo mbeanInfo)
    {
        _mbeanInfoMap.put(mbean.getUniqueName(), mbeanInfo);
    }    
    public MBeanInfo getMBeanInfo(ManagedBean mbean)
    {
        return _mbeanInfoMap.get(mbean.getUniqueName());
    }
    
    public void setNotificationInfo(ManagedBean mbean, List<NotificationInfoModel>value)
    {
        _notificationInfoMap.put(mbean.getUniqueName(), value);
    }
    
    public List<NotificationInfoModel> getNotificationInfo(ManagedBean mbean)
    {
        return _notificationInfoMap.get(mbean.getUniqueName());
    }
    
    public void addNotification(ObjectName objName, Notification notification)
    {
        List<NotificationObject> list = _notificationsMap.get(objName.toString());
        NotificationObject obj = new NotificationObject(notification.getSequenceNumber(),
                                                        new Date(notification.getTimeStamp()),
                                                        notification.getMessage(),
                                                        notification.getSource(),
                                                        notification.getType());
        
        if (list == null)
        {
            list = new ArrayList<NotificationObject>();
            _notificationsMap.put(objName.toString(), list);
        }
        
        list.add(obj);
    }
    
    public List<NotificationObject> getNotifications(ManagedBean mbean)
    {
        return _notificationsMap.get(mbean.getUniqueName());
    }
    
    public void clearNotifications(ManagedBean mbean)
    {
        if (_notificationsMap.containsKey(mbean.getUniqueName()))
            _notificationsMap.get(mbean.getUniqueName()).clear();
    }
    
    public void addNotificationListener(ManagedBean mbean, String name, String type)
    {
         HashMap<String, List<String>> map = _subscribedNotificationMap.get(mbean.getUniqueName());
        if (map == null)
        {
            map = new HashMap<String, List<String>>();
            _subscribedNotificationMap.put(mbean.getUniqueName(),map);
        }
        
        List<String> list = map.get(name);
        if (list == null)
        {
            list = new ArrayList<String>();
            map.put(name, list);
        }
        if (Constants.ALL.equals(type))
        {
            List<NotificationInfoModel> infoList = _notificationInfoMap.get(mbean.getUniqueName());
            for (NotificationInfoModel model : infoList)
            {                
                if (model.getName().equals(name))
                {
                    String[] types = model.getTypes();
                    for (int i = 0; i < types.length; i++)
                    {
                        list.add(types[i]);
                    }
                }
            }
        }
        else
        {
            list.add(type);
        }

        System.out.println("Subscribed for notification :" + mbean.getUniqueName());
    }
    
    public boolean hasSubscribedForNotifications(ManagedBean mbean, String name, String type)
    {
        if (_subscribedNotificationMap.containsKey(mbean.getUniqueName()))
        {
            HashMap<String, List<String>> map = _subscribedNotificationMap.get(mbean.getUniqueName());
            if (map.containsKey(name))
            {
                if (map.get(name).contains(type))
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    public void removeNotificationListener(ManagedBean mbean, String name, String type) throws Exception
    {
        System.out.println("Removed notification listener :" + mbean.getUniqueName() + name +type);
        if (_subscribedNotificationMap.containsKey(mbean.getUniqueName()))
        {            
            HashMap<String, List<String>> map = _subscribedNotificationMap.get(mbean.getUniqueName());
            if (map.containsKey(name))
            {
                if (Constants.ALL.equals(type))
                {
                    map.remove(name);
                }
                else if (type != null)
                {
                    map.get(name).remove(type);
                }
            }
            
            JMXManagedObject jmxbean = (JMXManagedObject)mbean;
            _mbsc.removeNotificationListener(jmxbean.getObjectName(), _notificationListener);
        }
    }
    
    public void registerManagedObject(ObjectName objName)
    {
        JMXManagedObject managedObject = new JMXManagedObject(objName);
        managedObject.setServer(getManagedServer());
        _mbeansToBeAdded.add(managedObject);
    }
    
    public void unregisterManagedObject(ObjectName objName)
    {
        JMXManagedObject managedObject = new JMXManagedObject(objName);
        managedObject.setServer(getManagedServer());
        _mbeansToBeRemoved.add(managedObject);
    }
    
    public List<ManagedBean> getObjectsToBeAdded()
    {
        if (_mbeansToBeAdded.isEmpty())
            return null;
        else
        {
            List<ManagedBean> list = _mbeansToBeAdded;
            _mbeansToBeAdded = new ArrayList<ManagedBean>();
            return list;
        }
    }

    public List<ManagedBean> getObjectsToBeRemoved()
    {
        if (_mbeansToBeRemoved.isEmpty())
            return null;
        else
        {
            List<ManagedBean> list = new CopyOnWriteArrayList<ManagedBean>(_mbeansToBeRemoved);
            _mbeansToBeRemoved.clear();
            return list;
        }
    }   
    
    public void setAttributeModel(ManagedBean mbean, ManagedAttributeModel value)
    {
        _attributeModelMap.put(mbean.getUniqueName(), value);
    }
    
    public ManagedAttributeModel getAttributeModel(ManagedBean mbean)
    {
        return _attributeModelMap.get(mbean.getUniqueName());
    }
    
    public void setOperationModel(ManagedBean mbean, OperationDataModel value)
    {
        _operationModelMap.put(mbean.getUniqueName(), value);
    }
    
    public OperationDataModel getOperationModel(ManagedBean mbean)
    {
        return _operationModelMap.get(mbean.getUniqueName());
    }
    
    public String[] getQueueNames()
    {
        return _queues.toArray(new String[0]);
    }
    
    public String[] getExchangeNames()
    {
        return _exchanges.toArray(new String[0]);
    }

    public ClientNotificationListener getNotificationListener()
    {
        return _notificationListener;
    }

    public ClientListener getClientListener()
    {
        return _clientListener;
    }
}
