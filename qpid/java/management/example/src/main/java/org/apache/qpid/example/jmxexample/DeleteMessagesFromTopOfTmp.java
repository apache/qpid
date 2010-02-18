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
package org.apache.qpid.example.jmxexample;

import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Connects to a server and queries all info for tmp_* named queues, determines 
 * their message count, and if this is above a given threshold deletes the 
 * specified number of messages from the front of the queue
 */
public class DeleteMessagesFromTopOfTmp
{
    /**
     * Params:
     * 0: host, e.g. myserver.mydomain.com
     * 1: port, e.g. 8999
     * 2: Number of messages to delete, e.g. 1000
     * 3: Threshold MessageCount on queue required before deletion will be undertaken e.g. 5000
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length < 4)
        {
            System.out.println("Usage: ");
            System.out.println("<host> <port> <numMsgsToDel> <minRequiredQueueMsgCount>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int numToDel = Integer.parseInt(args[2]);
        int numRequired = Integer.parseInt(args[3]);

        deleteFromTop(host, port, numToDel, numRequired);
    }

    private static void deleteFromTop(String host, int port, 
                                      int numMsgsToDel, int minRequiredQueueMsgCount) throws Exception
    {
        JMXConnector con = getJMXConnection(host, port);
        MBeanServerConnection mbsc = con.getMBeanServerConnection();

        // Gets all tmp_* queue MBean ObjectNames
        Set<ObjectName> names = mbsc.queryNames(
                                new ObjectName("org.apache.qpid:type=VirtualHost.Queue,name=tmp_*,*"), null);

        // Traverse objects and delete specified number of message if the min msg count is breached
        for (ObjectName queueObjectName : names)
        {
            String queueName = queueObjectName.getKeyProperty("name");
            System.out.println("Checking message count on queue: " + queueName);

            long mc = (Integer) mbsc.getAttribute(queueObjectName, "MessageCount");

            if(mc >= minRequiredQueueMsgCount)
            {
                System.out.println("MessageCount (" + mc + ") is above the specified threshold ("
                                   + minRequiredQueueMsgCount + ")");
                System.out.println("Deleting first " + numMsgsToDel + " messages on queue: " + queueName);

                int i;
                for(i=0; i<numMsgsToDel; i++)
                {
                    try
                    {
                        mbsc.invoke(queueObjectName,"deleteMessageFromTop",null,null);
                    }
                    catch(Exception e)
                    {
                        System.out.println("Exception whilst deleting message" + i +" from queue: " +e);
                        break;
                    }
                }
            }
            else
            {
                System.out.println("MessageCount (" + mc + ") is below the specified threshold ("
                                   + minRequiredQueueMsgCount + ")");
                System.out.println("Not deleting any messages on queue: " + queueName);
            }
        }
    }

    private static JMXConnector getJMXConnection(String host, int port) throws Exception
    {
        //Open JMX connection
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
        JMXConnector con = JMXConnectorFactory.connect(jmxUrl);
        return con;
    }
}



