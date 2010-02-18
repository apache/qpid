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

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.SSLException;


/**
 * Connects to a server and queries all info for Queues
 * Includes _tmp queues thus covering queues underlying topic subscriptions
 */
public class QueueInformation {

    private static long previousTimePoint = 0l;
    private static Map<String, Long> previousRMC = new HashMap<String, Long>();
    private static Map<String, Long> previousMC = new HashMap<String, Long>();

    /**
     * Params:
     * 0: host, e.g. eqd-myserver.mydomain.com
     * 1: port, e.g. 8999
     * 2: vhost e.g. dev-only
     * 3: username, e.g. guest
     * 4: pwd, e.g. guest
     * 5: loop pause, no value indicates one-off, any other value is millisecs
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.out.println("Usage: ");
            System.out.println("<host> <port> <vhost> <username> <pwd> <loop pause time in millisecs>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String vhost = args[2];
        String usr = args[3];
        String pwd = args[4];
        long pause = args.length == 6 ? Long.parseLong(args[5]) : -1l;

        getDetails(30000, host, port, vhost, usr, pwd, pause, true);
        previousTimePoint = System.currentTimeMillis();

        if (pause > 0) {
            while (true) {
                Thread.currentThread().sleep(pause);
                getDetails(30000, host, port, vhost, usr, pwd, pause, false);
                previousTimePoint = System.currentTimeMillis();
            }
        }
    }

    private static void getDetails(int timeout, String host, int port, String vhost, String username, String password, long pause, boolean printHeader) throws Exception {
        JMXConnector con = getJMXConnection(timeout, host, port, username, password);
        MBeanServerConnection mbsc = con.getMBeanServerConnection();

        // Gets all Queues names
        Set<ObjectName> names = mbsc.queryNames(new ObjectName("org.apache.qpid:type=VirtualHost.Queue,*"), null);

        // Print header
        if (names.size() > 0 && printHeader) {
            System.out.print("Time,");

            MBeanAttributeInfo[] attInfo = ((MBeanInfo) mbsc.getMBeanInfo((ObjectName) names.toArray()[0])).getAttributes();
            for (int i = 0; attInfo.length > i; i++) {
                System.out.print(attInfo[i].getName() + ",");
            }

            // Include update rate calculations
            if (pause > 0) {
                System.out.print("Consumption rate,");
                System.out.print("Buildup rate");
            }
            System.out.print("\n");
        }

        // Traverse objects and print data on a row basis
        for (ObjectName object : names) {
            MBeanAttributeInfo[] attInfo = ((MBeanInfo) mbsc.getMBeanInfo(object)).getAttributes();

            if (object.getCanonicalKeyPropertyListString().indexOf("VirtualHost=" + vhost) >= 0) {
                String name = object.getKeyProperty("name");

                // Include the "ping" queue
                if (name.equals("ping")) {
                    System.out.print(Calendar.getInstance().getTime().toString().substring(11, 19) + ",");
                    for (int i = 0; attInfo.length > i; i++) {
                        System.out.print(mbsc.getAttribute(object, attInfo[i].getName()) + ",");
                    }
                    System.out.print("\n");

                    // Include the "tmp_*" queues
                } else if (name.indexOf("tmp") >= 0) {
                    System.out.print(Calendar.getInstance().getTime().toString().substring(11, 19) + ",");
                    for (int i = 0; attInfo.length > i; i++) {
                        System.out.print(mbsc.getAttribute(object, attInfo[i].getName()) + ",");
                    }

                    // Output consumption rate calc
                    if (pause > 0) {
                        double timeDelta = (System.currentTimeMillis() - previousTimePoint) / 1000.0f;

                        long rmc2 = (Long) mbsc.getAttribute(object, "ReceivedMessageCount");
                        long mc2 = (Integer) mbsc.getAttribute(object, "MessageCount");

                        long rmc1 = 0l;
                        if (previousRMC.get(name) != null) {
                            rmc1 = previousRMC.get(name);
                        }
                        long mc1 = 0l;
                        if (previousMC.get(name) != null) {
                            mc1 = previousMC.get(name);
                        }

                        if (rmc1 > 0) {
                            double consumptionRate = ((rmc2 - rmc1) - (mc2 - mc1)) / timeDelta;
                            System.out.print(consumptionRate);

                            System.out.print(",");

                            double buildupRate = (mc2 - mc1) / timeDelta;
                            System.out.print(buildupRate);
                        } else {
                            System.out.print("null");
                        }

                        previousRMC.put(name, rmc2);
                        previousMC.put(name, mc2);
                    }

                    System.out.print("\n");
                }
            }
        }
    }

    private static JMXConnector getJMXConnection(int timeout, String host, int port, String username, String password) throws SSLException, IOException, Exception {
        //Open JMX connection
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
        Map<String, Object> env = new HashMap<String, Object>();
        env.put(JMXConnector.CREDENTIALS, new String[]{username, password});
        JMXConnector con = JMXConnectorFactory.connect(jmxUrl, env);
        return con;
    }
}

