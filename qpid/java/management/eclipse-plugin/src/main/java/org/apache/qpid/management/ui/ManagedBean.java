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

import java.util.HashMap;

/**
 * Class representing a managed bean on the managed server
 * @author Bhupendra Bhardwaj
 *
 */
public abstract class ManagedBean extends ManagedObject
{
    private String _uniqueName = "";
    private String _domain = "";
    private String _type = "";
    private ManagedServer _server = null;
    private HashMap _properties = null;
    
    public String getProperty(String key)
    {
        return (String)_properties.get(key);
    }
    
    public HashMap getProperties()
    {
        return _properties;
    }
    public void setProperties(HashMap properties)
    {
        this._properties = properties;
    }
    public String getDomain()
    {
        return _domain;
    }
    public void setDomain(String domain)
    {
        this._domain = domain;
    }

    public ManagedServer getServer()
    {
        return _server;
    }
    public void setServer(ManagedServer server)
    {
        this._server = server;
    }
    public String getType()
    {
        return _type;
    }
    public void setType(String type)
    {
        this._type = type;
    }
    public String getUniqueName()
    {
        return _uniqueName;
    }
    public void setUniqueName(String uniqueName)
    {
        this._uniqueName = uniqueName;
    }
    
}
