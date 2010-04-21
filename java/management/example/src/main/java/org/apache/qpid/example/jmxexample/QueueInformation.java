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

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Connects to a server and queries all info for Queues
 * Includes _tmp queues thus covering queues underlying topic subscriptions
 */
public class QueueInformation
{

    private static long _previousTimePoint = 0l;
    private static Map<String, Long> _previousRMC = new HashMap<String, Long>();
    private static Map<String, Long> _previousMC = new HashMap<String, Long>();
    private static MBeanServerConnection _mbsc;
    private static final String DEFAULT_DATE_FORMAT = System.getProperty("qpid.dateFormat", "yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat _formatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);

    private static final String QUEUE_ARGS = "queues=";
    private static Set<String> _queueNames;
    private static final String ATTRIB_ARGS = "attributes=";
    private static Set<String> _attribNames;

    private static MBeanAttributeInfo[] _attribInfo;
    private static String _vhost;

    /**
     * Params:
     * 0: host, e.g. eqd-myserver.mydomain.com
     * 1: port, e.g. 8999
     * 2: vhost e.g. dev-only
     * 3: username, e.g. guest
     * 4: pwd, e.g. guest
     * 5: loop pause, no value indicates one-off, any other value is millisecs
     * ..: attributes=<csv attribute list> , queue=<csv queue list>
     * The queue list can use wildcards such as * and ?. Basically any value
     * that JMX will accept in the query string for t name='' value of the queue.
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length < 5 || args.length > 8)
        {
            System.out.println("Usage: ");
            System.out.println("<host> <port> <vhost> <username> <pwd> [<loop pause time in millisecs>] [queues=<queue list csv>] [attributes=<attribute list csv>]");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        _vhost = args[2];
        String usr = args[3];
        String pwd = args[4];
        long pause = -1;

        if (args.length > 5)
        {
            try
            {
                pause = Long.parseLong(args[5]);
            }
            catch (NumberFormatException nfe)
            {
                // If it wasn't a queue or attribute request then show error
                if (!!args[5].startsWith(QUEUE_ARGS) &&
                    !!args[5].startsWith(ATTRIB_ARGS))
                {
                    System.out.println("Unknown argument '" + args[5] + "'");
                    System.exit(1);
                }
            }
        }

        //Process remaining args        
        // Skip arg 5 if we have assigned pause a value
        int arg = (pause == -1) ? 5 : 6;
        for (; args.length > arg; arg++)
        {
            processCommandLine(args[arg]);
        }

        JMXConnector con = getJMXConnection(host, port, usr, pwd);

        _mbsc = con.getMBeanServerConnection();

        Set<ObjectName> names = _mbsc.queryNames(new ObjectName("org.apache.qpid:type=VirtualHost.Queue,VirtualHost=" + _vhost + ",*"), null);

        // Print header
        if (names.size() > 0)
        {
            System.out.print("Time");

            MBeanAttributeInfo[] attributeList = getAttributeList(names.toArray(new ObjectName[1])[0]);

            for (int i = 0; attributeList.length > i; i++)
            {
                System.out.print(", " + attributeList[i].getName());
            }

            // Include update rate calculations
            if (pause > 0)
            {
                System.out.print(", Consumption rate");
                System.out.print(", Receive rate");
            }
            System.out.print("\n");
        }
        else
        {
            System.out.println("No queues found on specified vhost unable to continue.");
            System.exit(1);
        }

        try
        {
            do
            {
                getDetails(pause > -1);
                if (pause > 0)
                {
                    _previousTimePoint = System.currentTimeMillis();
                    Thread.currentThread().sleep(pause);
                }
            }
            while (pause > 0);
        }
        finally
        {
            con.close();
        }
    }

    private static MBeanAttributeInfo[] getAttributeList(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException
    {
        if (_attribInfo == null)
        {
            MBeanAttributeInfo[] allAttribs = ((MBeanInfo) _mbsc.getMBeanInfo((ObjectName) name)).getAttributes();

            if (_attribNames != null && _attribNames.size() != 0)
            {
                LinkedList<MBeanAttributeInfo> tmpList = new LinkedList<MBeanAttributeInfo>();

                for (MBeanAttributeInfo attribute : allAttribs)
                {
                    if (_attribNames.contains(attribute.getName()))
                    {
                        tmpList.add(attribute);
                    }
                }

                _attribInfo = tmpList.toArray(new MBeanAttributeInfo[tmpList.size()]);
            }
            else
            {
                _attribInfo = allAttribs;
            }
        }
        return _attribInfo;
    }

    private static void processCommandLine(String arg)
    {
        if (arg.startsWith(QUEUE_ARGS))
        {
            String[] queues = arg.substring(arg.indexOf("=") + 1).split(",");

            _queueNames = new HashSet<String>();

            for (String queue : queues)
            {
                if (queue.length() > 0)
                {
                    _queueNames.add(queue);
                }
            }

            if (_queueNames.size() == 0)
            {
                System.out.println("No Queues specified on queue argument: '" + arg + "'");
                System.exit(1);
            }

        }
        else if (arg.startsWith(ATTRIB_ARGS))
        {
            String[] attribs = arg.substring(arg.indexOf("=") + 1).split(",");

            _attribNames = new HashSet<String>();

            for (String attrib : attribs)
            {
                if (attrib.length() > 0)
                {
                    _attribNames.add(attrib);
                }
            }

            if (_attribNames.size() == 0)
            {
                System.out.println("No Attributes specified on attribute argument: '" + arg + "'");
                System.exit(1);
            }
        }
        else
        {
            System.out.println("Unknown argument '" + arg + "'");
            System.exit(1);
        }

    }

    private static void getDetails(boolean printRates) throws Exception
    {
        for (ObjectName object : getMatchingObjects())
        {
            try
            {

                // There should normally be only one but allow queue Names such as tmp_*
                // Line format is
                // <time> <attributes value>, [<attribute value, ]* <consumption rate> <buildup rate>

                String name = object.getKeyProperty("name");

                Date todaysDate = new java.util.Date();

                System.out.print(_formatter.format(todaysDate));

                MBeanAttributeInfo[] attributeList = getAttributeList(object);

                for (int i = 0; attributeList.length > i; i++)
                {
                    System.out.print(", " + _mbsc.getAttribute(object, attributeList[i].getName()));
                }

                // Output consumption rate calc
                if (printRates)
                {
                    double timeDelta = (System.currentTimeMillis() - _previousTimePoint) / 1000.0f;

                    long rmc2 = (Long) _mbsc.getAttribute(object, "ReceivedMessageCount");
                    long mc2 = (Integer) _mbsc.getAttribute(object, "MessageCount");

                    long rmc1 = 0l;
                    if (_previousRMC.get(name) != null)
                    {
                        rmc1 = _previousRMC.get(name);
                    }
                    long mc1 = 0l;
                    if (_previousMC.get(name) != null)
                    {
                        mc1 = _previousMC.get(name);
                    }

                    // If we don't have a previous value then ensure we print 0
                    if (rmc1 == 0)
                    {
                        rmc1 = rmc2;
                    }

                    double consumptionRate = ((rmc2 - rmc1) - (mc2 - mc1)) / timeDelta;
                    System.out.print(", ");

                    System.out.print(String.format("%.2f", consumptionRate));

                    System.out.print(", ");
                    double buildupRate = (mc2 - mc1) / timeDelta;
                    System.out.print(String.format("%.2f", buildupRate));

                    _previousRMC.put(name, rmc2);
                    _previousMC.put(name, mc2);
                }
            }
            catch (InstanceNotFoundException e)
            {
                System.out.print(" ..queue has been removed.");
            }
            catch (Exception e)
            {
                System.out.print(" ..error :" + e.getMessage());
            }
            finally
            {
                System.out.print("\n");
            }

        }// for ObjectName
    }

    private static JMXConnector getJMXConnection(String host, int port, String username, String password) throws IOException
    {
        //Open JMX connection
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
        Map<String, Object> env = new HashMap<String, Object>();
        env.put(JMXConnector.CREDENTIALS, new String[]{username, password});
        JMXConnector con = JMXConnectorFactory.connect(jmxUrl, env);
        return con;
    }

    public static ObjectName[] getMatchingObjects() throws IOException, MalformedObjectNameException
    {

        // Gets all Queues names
        if (_queueNames == null)
        {
            _queueNames = new HashSet<String>();
            _queueNames.add("*");
        }

        Set<ObjectName> requestedObjects = new HashSet<ObjectName>();

        for (String queue : _queueNames)
        {
            Set<ObjectName> matchingObjects = _mbsc.queryNames(new ObjectName("org.apache.qpid:type=VirtualHost.Queue,VirtualHost=" + _vhost + ",name=" + queue + ",*"), null);

            if (!matchingObjects.isEmpty())
            {
                requestedObjects.addAll(matchingObjects);
            }
        }

        return requestedObjects.toArray(new ObjectName[requestedObjects.size()]);
    }
}

