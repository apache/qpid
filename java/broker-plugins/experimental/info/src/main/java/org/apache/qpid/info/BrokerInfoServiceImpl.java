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

/**
 * 
 * @author sorin
 * 
 *  Implementation for Info service
 */

package org.apache.qpid.info;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class BrokerInfoServiceImpl implements BrokerInfoService
{

    SortedMap<String, String> brokerInfoMap = new TreeMap<String, String>();

    private final List<String> qpidProps = Arrays.asList("QPID_HOME",
            "QPID_WORK", "java.class.path", "java.vm.name",
            "java.class.version", "os.arch", "os.name", "os.version",
            "sun.arch.data.model", "user.dir", "user.name", "user.timezone");

    private final BundleContext _ctx;

    public BrokerInfoServiceImpl(BundleContext ctx)
    {
        _ctx = ctx;
    }

    public Info<? extends Map<String, ?>> invoke()
    {
        // Get current time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
        brokerInfoMap.put("time", sdf.format(Calendar.getInstance().getTime()));
        // Get the hostname
        try
        {
            InetAddress addr = InetAddress.getLocalHost();
            String hostname = addr.getHostName();
            brokerInfoMap.put("hostname", hostname);
            brokerInfoMap.put("ip", addr.getHostAddress());
        } catch (UnknownHostException e)
        {
            //
        }
        // Dump system props
        Properties sysprops = System.getProperties();
        String propName;
        for (Iterator<Entry<Object, Object>> it = sysprops.entrySet()
                .iterator(); it.hasNext();)
        {
            Entry<Object, Object> en = it.next();
            propName = en.getKey().toString();
            if (qpidProps.indexOf(propName) >= 0)
            {
                brokerInfoMap.put(propName, en.getValue().toString());
            }
        }

        if (null == _ctx)
        {
            return new Info<SortedMap<String, String>>(brokerInfoMap);
        }

        ServiceReference sref;
        ServerConfiguration sc;
        try
        {
            sref = _ctx
                    .getServiceReference(ServerConfiguration.class.getName());
            sc = (ServerConfiguration) _ctx.getService(sref);
            if (null != sc)
            {
                brokerInfoMap.put("port", sc.getPorts().toString());
            }
        }
        catch (Exception e)
        {
            return new Info<SortedMap<String, String>>(brokerInfoMap);
        }

        return new Info<SortedMap<String, String>>(brokerInfoMap);
    }

}
