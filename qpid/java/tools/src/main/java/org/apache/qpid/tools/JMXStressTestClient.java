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
package org.apache.qpid.tools;


import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.tools.util.ArgumentsParser;

public class JMXStressTestClient
{

    public static void main(String[] args) throws Exception
    {
        ArgumentsParser parser = new ArgumentsParser();
        Arguments arguments;
        try
        {
            arguments = parser.parse(args, Arguments.class);
            arguments.validate();
        }
        catch(IllegalArgumentException e)
        {
            System.out.println("Invalid argument:" + e.getMessage());
            parser.usage(Arguments.class, Arguments.REQUIRED);
            System.out.println("\nRun example:");
            System.out.println("    java -cp qpid-tools.jar org.apache.qpid.tools.JMXStressTestClient \\");
            System.out.println("      repetitions=10 host=localhost port=8999 username=admin password=admin \\");
            System.out.println("      virtualHost=default createQueue=true bindQueue=true deleteQueue=true \\");
            System.out.println("      uniqueQueues=true queueName=boo exchangeName=amq.fanout");
            return;
        }

        JMXStressTestClient client = new JMXStressTestClient();
        client.run(arguments);
    }

    public void run(Arguments arguments) throws IOException,MalformedObjectNameException
    {
        log(arguments.toString());
        for (int i = 0; i < arguments.getRepetitions(); i++)
        {
            try(JMXConnector connector = createConnector(arguments.getHost(), arguments.getPort(), arguments.getUsername(), arguments.getPassword()))
            {
                runIteration(arguments, connector, i);
            }
        }
    }

    private void runIteration(Arguments arguments, JMXConnector connector, int iteration) throws IOException, MalformedObjectNameException
    {
        log("Iteration " + iteration);
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        String virtualHost = arguments.getVirtualHost();
        if (virtualHost != null)
        {
            ObjectName virtualHostMBeanName = new ObjectName("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost="
                    + ObjectName.quote(virtualHost));

            Set<ObjectName> virtualHostMBeans = connection.queryNames(virtualHostMBeanName, null);
            if(virtualHostMBeans.size() == 0)
            {
                throw new IllegalArgumentException("VirtualHost MBean was not found for virtual host " + virtualHost);
            }

            createAndBindQueueIfRequired(arguments, iteration, connection, virtualHostMBeanName);
        }
    }

    private void log(String logMessage)
    {
        System.out.println(logMessage);
    }

    private void createAndBindQueueIfRequired(Arguments arguments, int iteration, MBeanServerConnection connection,
                                              ObjectName virtualHostMBeanName) throws MalformedObjectNameException, IOException
    {
        if (arguments.isCreateQueue())
        {
            String queueName = arguments.getQueueName();

            if (queueName == null)
            {
                queueName = "temp-queue-" + System.nanoTime();
            }
            else if (arguments.isUniqueQueues())
            {
                queueName =  queueName + "-" + iteration;
            }

            createQueue(connection, virtualHostMBeanName, queueName);

            if (arguments.isBindQueue())
            {
                bindQueue(connection, arguments.getVirtualHost(), queueName, arguments.getExchangeName());
            }

            if (arguments.isDeleteQueue())
            {
                deleteQueue(connection, virtualHostMBeanName, queueName);
            }
        }
    }

    private void deleteQueue(MBeanServerConnection connection, ObjectName virtualHostMBeanName, String queueName)
    {
        log("    Delete queue " + queueName);
        try
        {
            connection.invoke(virtualHostMBeanName, "deleteQueue", new Object[]{queueName}, new String[]{String.class.getName()});
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot delete queue " + queueName, e);
        }
    }

    private void createQueue(MBeanServerConnection connection, ObjectName virtualHostMBeanName, String queueName)
    {
        log("    Create queue " + queueName);
        try
        {
            connection.invoke(virtualHostMBeanName, "createNewQueue", new Object[]{queueName, null, true},
                    new String[]{String.class.getName(), String.class.getName(), boolean.class.getName()});
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot create queue " + queueName, e);
        }
    }

    private void bindQueue(MBeanServerConnection connection, String virtualHost, String queueName, String exchangeName)
            throws MalformedObjectNameException, IOException
    {
        if (exchangeName == null)
        {
            exchangeName = "amq.direct";
        }

        log("        Bind queue " + queueName + " to " + exchangeName + " using binding key " + queueName);

        ObjectName exchangeObjectName = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost="
                + ObjectName.quote(virtualHost) + ","
                + "name=" + ObjectName.quote(exchangeName) + ",ExchangeType=*");

        Set<ObjectName> exchanges = connection.queryNames(exchangeObjectName, null);

        if(exchanges.size() == 0)
        {
            throw new IllegalArgumentException("Cannot find exchange MBean for exchange " + exchangeName);
        }

        try
        {
            connection.invoke(exchanges.iterator().next(), "createNewBinding", new Object[]{queueName, queueName},
                    new String[]{String.class.getName(), String.class.getName()});
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot delete queue " + queueName, e);
        }
    }

    JMXConnector createConnector(String host, int port, String username, String password) throws IOException
    {
        Map<String, Object> env = new HashMap<>();
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
        env.put(JMXConnector.CREDENTIALS, new String[] {username,password});

        return JMXConnectorFactory.connect(jmxUrl, env);
    }

    public static class Arguments
    {
        private static final Set<String> REQUIRED = new HashSet<>(Arrays.asList("host", "port", "username", "password"));

        private String host = null;
        private int port = -1;
        private String username = null;
        private String password = null;

        private String virtualHost = null;
        private String queueName = null;
        private String exchangeName = null;

        private int repetitions = 1;

        private boolean createQueue = false;
        private boolean deleteQueue = false;
        private boolean uniqueQueues = false;
        private boolean bindQueue = false;

        public Arguments()
        {
        }

        public void validate()
        {
            if (host == null || host.equals(""))
            {
                throw new IllegalArgumentException("Mandatory argument 'host' is not specified");
            }

            if (port == -1)
            {
                throw new IllegalArgumentException("Mandatory argument 'port' is not specified");
            }

            if (username == null || username.equals(""))
            {
                throw new IllegalArgumentException("Mandatory argument 'username' is not specified");
            }

            if (password == null || password.equals(""))
            {
                throw new IllegalArgumentException("Mandatory argument 'password' is not specified");
            }
        }

        public int getRepetitions()
        {
            return repetitions;
        }

        public String getHost()
        {
            return host;
        }

        public int getPort()
        {
            return port;
        }

        public String getUsername()
        {
            return username;
        }

        public String getPassword()
        {
            return password;
        }

        public String getVirtualHost()
        {
            return virtualHost;
        }

        public boolean isCreateQueue()
        {
            return createQueue;
        }

        public boolean isDeleteQueue()
        {
            return deleteQueue;
        }

        public boolean isUniqueQueues()
        {
            return uniqueQueues;
        }

        public String getQueueName()
        {
            return queueName;
        }

        public boolean isBindQueue()
        {
            return bindQueue;
        }

        public String getExchangeName()
        {
            return exchangeName;
        }

        @Override
        public String toString()
        {
            return "Arguments{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    ", username='" + username + '\'' +
                    ", password='" + password + '\'' +
                    ", virtualHost='" + virtualHost + '\'' +
                    ", queueName='" + queueName + '\'' +
                    ", exchangeName='" + exchangeName + '\'' +
                    ", repetitions=" + repetitions +
                    ", createQueue=" + createQueue +
                    ", deleteQueue=" + deleteQueue +
                    ", uniqueQueues=" + uniqueQueues +
                    ", bindQueue=" + bindQueue +
                    '}';
        }
    }

}
