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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ManagedServer;
import org.apache.qpid.management.ui.model.AttributeData;
import org.apache.qpid.management.ui.model.ManagedAttributeModel;
import org.apache.qpid.management.ui.model.NotificationInfoModel;
import org.apache.qpid.management.ui.model.OperationData;
import org.apache.qpid.management.ui.model.OperationDataModel;
import org.apache.qpid.management.ui.model.ParameterData;
import org.apache.qpid.management.ui.views.ViewUtility;


public class MBeanUtility
{

    public static MBeanInfo getMBeanInfo(ManagedBean mbean)
        throws IOException, JMException
    {
        ManagedServer server = mbean.getServer();
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(server);

        MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        if (mbsc == null)
            System.out.println("MBeanServerConnection does not exist in the Application registry.");
        
        JMXManagedObject jmxbean = (JMXManagedObject)mbean;
        MBeanInfo mbeanInfo = mbsc.getMBeanInfo(jmxbean.getObjectName());
        serverRegistry.putMBeanInfo(mbean, mbeanInfo);
        
        getAttributes(mbean);
        getOperations(mbean);
        
        return mbeanInfo;
    }
    
    
    public static Object execute(ManagedBean mbean, OperationData opData) throws Exception
    {
        String opName = opData.getName();
        Object[] values = null;
        String[] signature = null;
        
        List<ParameterData> params = opData.getParameters();
        if (params != null && !params.isEmpty())
        {
            signature = new String[params.size()];;
            values = new Object[params.size()];
            for (int i = 0; i < params.size(); i++)
            {
                signature[i] = params.get(i).getType();
                values[i] = params.get(i).getValue();
                System.out.println(params.get(i).getName() +  " : " + params.get(i).getValue());
            }
        }
        
        ManagedServer server = mbean.getServer();
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(server);

        MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        if (mbsc == null)
        {
            System.out.println("MBeanServerConnection doesn't exist in the Application registry.");
            // TODO
            // throw exception to check if the server is added 
            // Or try and get the connection again if it was disconnected
            return null;
        }
        JMXManagedObject jmxbean = (JMXManagedObject)mbean;
        return mbsc.invoke(jmxbean.getObjectName(), opName, values, signature);
        
        /*
        try
        {
            
        }
        catch(MBeanException ex)
        {
            ex.printStackTrace();
            
        } 
        catch(OperationsException ex)
        {
            ex.printStackTrace();
            
        }
        catch(JMException ex)
        {
            ex.printStackTrace();
            ViewUtility.popupError(new Exception(ex), "Operation failed");
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
            ViewUtility.popupError(new Exception(ex), "Operation failed");
        }
        */
    }
    
    public static void handleException(Exception ex)
    {
        handleException(null, ex);
    }
    
    public static void handleException(ManagedBean mbean, Exception ex)
    {
        if (mbean == null)
        {
            ViewUtility.popupErrorMessage("Error", ex.getMessage());
        }
        else if (ex instanceof IOException)
        {
            ViewUtility.popupErrorMessage(mbean.getName(), ex.getMessage());
        }
        else if (ex instanceof ReflectionException)
        {
            ViewUtility.popupErrorMessage(mbean.getName(), ex.getMessage());
        }
        else if (ex instanceof InstanceNotFoundException)
        {
            ViewUtility.popupErrorMessage(mbean.getName(), ex.getMessage());
        }
        else if (ex instanceof MBeanException)
        {
            ViewUtility.popupInfoMessage(mbean.getName(), ex.getMessage());
        }
        else
        {
            ViewUtility.popupError(mbean.getName(), "Error occured", ex);
        }
        ex.printStackTrace();
    }
    
    public static void createNotificationlistener(ManagedBean mbean, String name, String type)
        throws IOException, Exception
    {
        JMXManagedObject jmxbean = (JMXManagedObject)mbean;
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);
        serverRegistry.addNotificationListener(mbean, name, type);
        MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        
        if (mbsc == null)
        {
            throw new Exception("MBeanServer connection is broken");
        }
        mbsc.addNotificationListener(jmxbean.getObjectName(), serverRegistry.getNotificationListener(), null, null);
        System.out.println("Listener created : " + jmxbean.getObjectName());
    }
    
    public static void removeNotificationListener(ManagedBean mbean, String name, String type) throws Exception
    {
        //JMXManagedObject jmxbean = (JMXManagedObject)mbean;
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);
        serverRegistry.removeNotificationListener(mbean, name, type);
        //MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        
        //if (mbsc != null)
        //{
          //  mbsc.removeNotificationListener(jmxbean.getObjectName(), serverRegistry.getNotificationListener());
        //}
    }
    
    public static int refreshAttribute(ManagedBean mbean, String attribute) throws Exception
    {
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);
        MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        
        if (mbsc == null)
            throw new Exception("Server connection is not available for " + mbean.getUniqueName());
        
        Object value = mbsc.getAttribute(((JMXManagedObject)mbean).getObjectName(), attribute);
        
        ManagedAttributeModel attributeModel = serverRegistry.getAttributeModel(mbean);
        attributeModel.setAttributeValue(attribute, value);
        return Integer.parseInt(String.valueOf(value));
    }
    
    public static ManagedAttributeModel getAttributes(ManagedBean mbean)
    {
        ObjectName objName = ((JMXManagedObject)mbean).getObjectName();
        String[] attributes = null;
        AttributeList list = null;
        
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);
        MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        MBeanAttributeInfo[] attributesInfo = null;
        ManagedAttributeModel attributeModel = serverRegistry.getAttributeModel(mbean);
        // If retrieving attributeInfo for the first time.
        if (attributeModel == null)
        {
            attributeModel = new ManagedAttributeModel();
            attributesInfo = serverRegistry.getMBeanInfo(mbean).getAttributes();
            attributes = new String[attributesInfo.length];
            for (int i = 0; i< attributesInfo.length ; i++)
            {
                attributes[i] = attributesInfo[i].getName();
                attributeModel.setAttributeDescription(attributes[i], attributesInfo[i].getDescription());
                attributeModel.setAttributeWritable(attributes[i], attributesInfo[i].isWritable());
                attributeModel.setAttributeReadable(attributes[i], attributesInfo[i].isReadable());
            }
        }  
        else
        {
            attributes = attributeModel.getAttributeNames().toArray(new String[0]);
        }
        
        try
        {
            if (attributes.length != 0)
            {
                list = mbsc.getAttributes(objName, attributes);
                for (Iterator itr = list.iterator(); itr.hasNext();)
                {
                    Attribute attrib = (Attribute)itr.next();
                    attributeModel.setAttributeValue(attrib.getName(), attrib.getValue());
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }               
        
        serverRegistry.setAttributeModel(mbean, attributeModel);
        
        return attributeModel;
    }
    
    public static void updateAttribute(ManagedBean mbean, AttributeData attribute, String value)
    {
        JMXManagedObject jmxbean = (JMXManagedObject)mbean;
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);

        MBeanServerConnection mbsc = serverRegistry.getServerConnection();
        
        Object newValue = value;
        if (attribute.getDataType().equals(String.class.getName()))
        {
            
        }
        else if (attribute.getDataType().equals(Long.class.getName()))
        {
            newValue = new Long(Long.parseLong(value));
        }
        else if (attribute.getDataType().equals(Integer.class.getName()))
        {
            newValue = new Integer(Integer.parseInt(value));
        }
        
        try
        {
            mbsc.setAttribute(jmxbean.getObjectName(), new Attribute(attribute.getName(), newValue));
            
            // Update the value in the registry, to avoid refreshing from mbsc
            ManagedAttributeModel attributeModel = serverRegistry.getAttributeModel(mbean);
            attributeModel.setAttributeValue(attribute.getName(), newValue);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static OperationDataModel getOperations(ManagedBean mbean)
    {
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);

        OperationDataModel dataModel = serverRegistry.getOperationModel(mbean);
        if (dataModel == null)
        {
            MBeanInfo mbeanInfo = serverRegistry.getMBeanInfo(mbean);
            MBeanOperationInfo[] operationsInfo = mbeanInfo.getOperations();
            dataModel = new OperationDataModel();
            
            for (int i = 0; i < operationsInfo.length; i++)
            {
                dataModel.addOperation(operationsInfo[i]);
            }
            
            serverRegistry.setOperationModel(mbean, dataModel);
        }
        
        return dataModel;
    }
    
    public static NotificationInfoModel[] getNotificationInfo(ManagedBean mbean)
    {
        
        JMXServerRegistry serverRegistry = (JMXServerRegistry)ApplicationRegistry.getServerRegistry(mbean);
        MBeanNotificationInfo[] info = serverRegistry.getMBeanInfo(mbean).getNotifications();
        
        if (info == null || info.length == 0)
            return null;
        
        List<NotificationInfoModel> list = serverRegistry.getNotificationInfo(mbean);
        
        if (list != null) 
            return list.toArray(new NotificationInfoModel[0]);
        else
            list = new ArrayList<NotificationInfoModel>();
        
        for (int i = 0; i < info.length; i++)
        {
            list.add(new NotificationInfoModel(info[i].getName(), info[i].getDescription(), info[i].getNotifTypes()));
        }
        serverRegistry.setNotificationInfo(mbean, list);
        
        return list.toArray(new NotificationInfoModel[0]);
    }
}
