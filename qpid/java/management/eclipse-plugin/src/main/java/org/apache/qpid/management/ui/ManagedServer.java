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

/**
 * Class representing a server being managed eg. MBeanServer
 * @author Bhupendra Bhardwaj
 */
public class ManagedServer extends ManagedObject
{
    private String host;
    private String port;
    private String url;
    private String domain;
    
    public ManagedServer(String host, String port, String domain)
    {
        this.host = host;
        this.port = port;
        this.domain = domain;
        setName(host + ":" + port);
    }
    
    public ManagedServer(String url, String domain)
    {
        this.url = url;
        this.domain = domain;
    }

    public String getDomain()
    {
        return domain;
    }

    public String getHost()
    {
        return host;
    }

    public String getPort()
    {
        return port;
    }

    public String getUrl()
    {
        return url;
    }

    public void setHostAndPort(String host, String port)
    {
        this.host = host;
        this.port = port;
        setName(host + ":" + port);
    }
    
    public void setUrl(String url)
    {
        this.url = url;
    }
}
