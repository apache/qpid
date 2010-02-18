/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 * 
 */
package org.apache.qpid.example.jmxexample;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedExchange;

public class AddQueue
{

    public static void main(String[] args)
    {
        //Example: add 'newqueue' to the 'test' virtualhost and bind to the 'amq.direct' exchange
        //TODO: take these parameters as arguments
        
        addQueue("test", "amq.direct", "newqueue");
    }
    
    private static JMXConnector getJMXConnection() throws Exception
    {
        //TODO: Take these parameters as main+method arguments
        String host = "localhost";
        int port = 8999;
        String username = "admin";
        String password = "admin";
        
        Map<String, Object> env = new HashMap<String, Object>();
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");

        //Add user credential's to environment map for RMIConnector startup. 
        env.put(JMXConnector.CREDENTIALS, new String[] {username,password});
        
        return JMXConnectorFactory.connect(jmxUrl, env);
    }
    
    public static boolean addQueue(String virHost, String exchName, String queueName) {

        JMXConnector jmxc = null;
        try 
        {
            jmxc = getJMXConnection();
            
            MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

            ObjectName hostManagerObjectName = new ObjectName( 
                    "org.apache.qpid:" + 
                    "type=VirtualHost.VirtualHostManager," + 
                    "VirtualHost=" + virHost + ",*"); 

            Set<ObjectName> vhostManagers = mbsc.queryNames(hostManagerObjectName, null);
            
            if(vhostManagers.size() == 0)
            {
                //The vhostManager MBean wasnt found, cant procede
                return false;
            }
            
            ManagedBroker vhostManager = (ManagedBroker) MBeanServerInvocationHandler.newProxyInstance(
                                              mbsc, (ObjectName) vhostManagers.toArray()[0], ManagedBroker.class, false);
                        
            ObjectName customExchangeObjectName = new ObjectName(
                    "org.apache.qpid:" +
                    "type=VirtualHost.Exchange," +
                    "VirtualHost=" + virHost + "," +
                    "name=" + exchName + "," + 
                    "ExchangeType=direct,*");

            Set<ObjectName> exchanges = mbsc.queryNames(customExchangeObjectName, null);
            
            if(exchanges.size() == 0)
            {
                //The exchange doesnt exist, cant procede.
                return false;
            }

            //create the MBean proxy
            ManagedExchange managedExchange = (ManagedExchange) MBeanServerInvocationHandler.newProxyInstance(
                        mbsc, (ObjectName) exchanges.toArray()[0], ManagedExchange.class, false);
              
            try
            {
                //create the new durable queue and bind it.
                vhostManager.createNewQueue(queueName, null, true);
                managedExchange.createNewBinding(queueName,queueName);
            }
            catch (Exception e)
            {
                System.out.println("Could not add queue due to exception :" + e.getMessage());
                e.printStackTrace();
                return false;
            }

            return true;

        }
        catch (Exception e)
        {
            System.out.println("Could not add queue due to error :" + e.getMessage());
            e.printStackTrace();
        } 
        finally
        {
            if(jmxc != null)
            {
                try
                {
                    jmxc.close();
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }
                
        return false;
        
    }

}
