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
package org.apache.qpid.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BrokerOptions
{
    /** serialVersionUID */
    private static final long serialVersionUID = 8051825964945442234L;

    public static final String DEFAULT_CONFIG_FILE = "etc/config.xml";
    public static final String DEFAULT_LOG_CONFIG_FILE = "etc/log4j.xml";
    public static final String QPID_HOME = "QPID_HOME";

    public static final String PORTS = "p";
    public static final String SSL_PORTS = "s";
    public static final String BIND = "b";
    public static final String MANAGEMENT = "m";
    public static final String LOG4J = "l";
    public static final String WATCH = "w";
    public static final String CONFIG = "c";
    public static final String PROTOCOL = "protocol";

    private final Set<Integer> _ports = new HashSet<Integer>();
    private final Set<Integer> _sslPorts = new HashSet<Integer>();
    private final Map<ProtocolExclusion,Set<Integer>> _exclusionMap = new HashMap<ProtocolExclusion, Set<Integer>>();

    private String _configFile;
    private String _logConfigFile;
    private String _bind;
    private String _transport = Transport.TCP;
    private Integer _jmxPort;

    private Integer _logWatchFrequency = 0;

    public void addPort(final int port)
    {
        _ports.add(port);
    }

    public void addSSLPort(final int sslPort)
    {
        _sslPorts.add(sslPort);
    }

    public Set<Integer> getPorts()
    {
        return Collections.unmodifiableSet(_ports);
    }

    public Set<Integer> getSSLPorts()
    {
        return Collections.unmodifiableSet(_sslPorts);
    }

    public String getConfigFile()
    {
        return _configFile;
    }

    public void setConfigFile(final String configFile)
    {
        _configFile = configFile;
    }

    public String getLogConfigFile()
    {
        return _logConfigFile;
    }

    public void setLogConfigFile(final String logConfigFile)
    {
        _logConfigFile = logConfigFile;
    }

    public Integer getJmxPort()
    {
        return _jmxPort;
    }

    public void setJmxPort(final int jmxPort)
    {
        _jmxPort = jmxPort;
    }

    public String getQpidHome()
    {
        return System.getProperty(QPID_HOME);
    }

    public Set<Integer> getExcludedPorts(final ProtocolExclusion excludeProtocol)
    {
        final Set<Integer> excludedPorts = _exclusionMap.get(excludeProtocol);
        return excludedPorts == null ? Collections.<Integer>emptySet() : excludedPorts;
    }

    public void addExcludedPort(final ProtocolExclusion excludeProtocol, final int port)
    {
        if (!_exclusionMap.containsKey(excludeProtocol))
        {
            _exclusionMap.put(excludeProtocol, new HashSet<Integer>());
        }

        Set<Integer> ports = _exclusionMap.get(excludeProtocol);
        ports.add(port);
    }

    public String getBind()
    {
        return _bind;
    }

    public void setBind(final String bind)
    {
        _bind = bind;
    }

    public int getLogWatchFrequency()
    {
        return _logWatchFrequency;
    }

    public void setLogWatchFrequency(final int logWatchFrequency)
    {
        _logWatchFrequency = logWatchFrequency;
    }

    public String getTransport()
    {
        return _transport;
    }

    public void setTransport(final String transport)
    {
        _transport = transport;
    }
}