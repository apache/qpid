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

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.log4j.Logger;

public class PrincipalDatabaseAccessManager implements AccessManager
{
    private static final Logger _logger = Logger.getLogger(PrincipalDatabaseAccessManager.class);

    PrincipalDatabase _database;
    AccessManager _default;

    public PrincipalDatabaseAccessManager()
    {
            _default = ApplicationRegistry.getInstance().getAccessManager();
    }

    public void setDefaultAccessManager(String defaultAM)
    {
        if (defaultAM.equals("AllowAll"))
        {
            _default = new AllowAll();
        }

        if (defaultAM.equals("DenyAll"))
        {
            _default = new DenyAll();
        }
    }

    public void setPrincipalDatabase(String database)
    {
        _database = ApplicationRegistry.getInstance().getDatabaseManager().getDatabases().get(database);
        if (!(_database instanceof AccessManager))
        {
            _logger.warn("Database '" + database + "' cannot perform access management");
        }
    }

    public AccessResult isAuthorized(Accessable accessObject, String username)
    {
        AccessResult result;

        if (_database == null)
        {
            result = _default.isAuthorized(accessObject, username);
        }
        else
        {
            result = ((AccessManager) _database).isAuthorized(accessObject, username);
        }

        result.addAuthorizer(this);

        return result;
    }

    public String getName()
    {
        return "PrincipalDatabaseFileAccessManager";
    }

}
