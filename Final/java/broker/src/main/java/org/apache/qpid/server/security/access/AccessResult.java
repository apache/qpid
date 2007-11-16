/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.access;

public class AccessResult
{
    public enum AccessStatus
    {
        GRANTED, REFUSED
    }

    StringBuilder _authorizer;
    AccessStatus _status;

    public AccessResult(AccessManager authorizer, AccessStatus status)
    {
        _status = status;
        _authorizer = new StringBuilder(authorizer.getName());        
    }

    public void setAuthorizer(AccessManager authorizer)
    {
        _authorizer.append(authorizer.getName());
    }

    public String getAuthorizer()
    {
        return _authorizer.toString();
    }

    public void setStatus(AccessStatus status)
    {
        _status = status;
    }

    public AccessStatus getStatus()
    {
        return _status;
    }

    public void addAuthorizer(AccessManager accessManager)
    {
        _authorizer.insert(0, "->");
        _authorizer.insert(0, accessManager.getName());
    }


}
