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
package org.apache.qpid.management.ui;

import static org.apache.qpid.management.ui.Constants.DEFAULT_PROTOCOL;
/**
 * Class representing a server being managed eg. MBeanServer
 * @author Bhupendra Bhardwaj
 */
public class ManagedServer extends ManagedObject
{
    private String _host;
    private int _port;
    private String _url;
    private String _domain;
    private String _user;
    private String _password;
    private String _protocol = DEFAULT_PROTOCOL;

    public ManagedServer(String host, int port, String domain)
    {
        this(host, port, domain, null, null);
    }
    
    public ManagedServer(String host, int port, String domain, String user, String password)
    {
        setName(host + ":" + port);
        _host = host;
        _port = port;
        _domain = domain;
        _url = getRMIURL(host, port);
        _user = user;
        _password = password;
    }
    
    public String getDomain()
    {
        return _domain;
    }

    public String getHost()
    {
        return _host;
    }

    public int getPort()
    {
        return _port;
    }

    public String getUrl()
    {
        return _url;
    }
    
    public String getProtocol()
    {
        return _protocol;
    }

    public String getPassword()
    {
        return _password;
    }
    
    public void setPassword(String password)
    {
        _password = password;
    }

    public String getUser()
    {
        return _user;
    }
    
    public void setUser(String user)
    {
        _user = user;
    }
    
    private String getRMIURL(String host, int port)
    {
        return "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
    }
}
