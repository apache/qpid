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

package org.apache.qpid.amqp_1_0.transport;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

public class Container
{

    private static final AtomicInteger CONTAINER_ID = new AtomicInteger(0);

    private String _id;

    public Container()
    {
        String hostname;
        try
        {
            InetAddress addr = InetAddress.getLocalHost();


            // Get hostname
            hostname = addr.getHostName();
        }
        catch (UnknownHostException e)
        {
            hostname="127.0.0.1";
        }

        String pid;
        String hackForPid = ManagementFactory.getRuntimeMXBean().getName();
        if(hackForPid != null && hackForPid.contains("@"))
        {
            pid = hackForPid.split("@")[0];
        }
        else
        {
            pid = "unknown";
        }

        _id = hostname + '(' + pid + ')' + ':' + CONTAINER_ID.incrementAndGet();

    }


    public Container(String id)
    {
        _id = id;
    }

    public String getId()
    {
        return _id;
    }


}
