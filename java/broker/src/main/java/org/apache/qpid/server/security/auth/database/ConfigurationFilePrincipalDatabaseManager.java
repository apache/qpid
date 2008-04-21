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
package org.apache.qpid.server.security.auth.database;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;

import org.apache.log4j.Logger;

import org.apache.qpid.configuration.PropertyUtils;
import org.apache.qpid.configuration.PropertyException;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.access.management.AMQUserManagementMBean;
import org.apache.qpid.AMQException;

import javax.management.JMException;

public class ConfigurationFilePrincipalDatabaseManager implements PrincipalDatabaseManager
{
    private static final Logger _logger = Logger.getLogger(ConfigurationFilePrincipalDatabaseManager.class);

    private static final String _base = "security.principal-databases.principal-database";

    Map<String, PrincipalDatabase> _databases;

    public ConfigurationFilePrincipalDatabaseManager(Configuration configuration) throws Exception
    {
        _logger.info("Initialising PrincipleDatabase authentication manager");
        _databases = initialisePrincipalDatabases(configuration);
    }

    private Map<String, PrincipalDatabase> initialisePrincipalDatabases(Configuration configuration) throws Exception
    {
        List<String> databaseNames = configuration.getList(_base + ".name");
        List<String> databaseClasses = configuration.getList(_base + ".class");
        Map<String, PrincipalDatabase> databases = new HashMap<String, PrincipalDatabase>();

        if (databaseNames.size() == 0)
        {
            _logger.warn("No Principal databases specified. Broker running with NO AUTHENTICATION");
        }

        for (int i = 0; i < databaseNames.size(); i++)
        {
            Object o;
            try
            {
                o = Class.forName(databaseClasses.get(i)).newInstance();
            }
            catch (Exception e)
            {
                throw new Exception("Error initialising principal database: " + e, e);
            }

            if (!(o instanceof PrincipalDatabase))
            {
                throw new Exception("Principal databases must implement the PrincipalDatabase interface");
            }

            initialisePrincipalDatabase((PrincipalDatabase) o, configuration, i);

            String name = databaseNames.get(i);
            if ((name == null) || (name.length() == 0))
            {
                throw new Exception("Principal database names must have length greater than or equal to one character");
            }

            PrincipalDatabase pd = databases.get(name);
            if (pd != null)
            {
                throw new Exception("Duplicate principal database name not provided");
            }

            _logger.info("Initialised principal database '" + name + "' successfully");
            databases.put(name, (PrincipalDatabase) o);
        }

        return databases;
    }

    private void initialisePrincipalDatabase(PrincipalDatabase principalDatabase, Configuration config, int index)
            throws FileNotFoundException, ConfigurationException
    {
        String baseName = _base + "(" + index + ").attributes.attribute.";
        List<String> argumentNames = config.getList(baseName + "name");
        List<String> argumentValues = config.getList(baseName + "value");
        for (int i = 0; i < argumentNames.size(); i++)
        {
            String argName = argumentNames.get(i);
            if ((argName == null) || (argName.length() == 0))
            {
                throw new ConfigurationException("Argument names must have length >= 1 character");
            }

            if (Character.isLowerCase(argName.charAt(0)))
            {
                argName = Character.toUpperCase(argName.charAt(0)) + argName.substring(1);
            }

            String methodName = "set" + argName;
            Method method = null;
            try
            {
                method = principalDatabase.getClass().getMethod(methodName, String.class);
            }
            catch (Exception e)
            {
                // do nothing.. as on error method will be null
            }

            if (method == null)
            {
                throw new ConfigurationException("No method " + methodName + " found in class "
                                                 + principalDatabase.getClass()
                                                 + " hence unable to configure principal database. The method must be public and "
                                                 + "have a single String argument with a void return type");
            }

            try
            {
                method.invoke(principalDatabase, PropertyUtils.replaceProperties(argumentValues.get(i)));
            }
            catch (Exception ite)
            {
                if (ite instanceof ConfigurationException)
                {
                    throw(ConfigurationException) ite;
                }
                else
                {
                    throw new ConfigurationException(ite.getMessage(), ite);
                }
            }
        }
    }

    public Map<String, PrincipalDatabase> getDatabases()
    {
        return _databases;
    }

    public void initialiseManagement(Configuration config) throws ConfigurationException
    {
        try
        {
            AMQUserManagementMBean _mbean = new AMQUserManagementMBean();

            String baseSecurity = "security.jmx";
            List<String> principalDBs = config.getList(baseSecurity + ".principal-database");

            if (principalDBs.size() == 0)
            {
                throw new ConfigurationException("No principal-database specified for jmx security(" + baseSecurity + ".principal-database)");
            }

            String databaseName = principalDBs.get(0);

            PrincipalDatabase database = getDatabases().get(databaseName);

            if (database == null)
            {
                throw new ConfigurationException("Principal-database '" + databaseName + "' not found");
            }

            _mbean.setPrincipalDatabase(database);

            List<String> jmxaccesslist = config.getList(baseSecurity + ".access");

            if (jmxaccesslist.size() == 0)
            {
                throw new ConfigurationException("No access control files specified for jmx security(" + baseSecurity + ".access)");
            }

            String jmxaccesssFile = null;

            try
            {
                jmxaccesssFile = PropertyUtils.replaceProperties(jmxaccesslist.get(0));
            }
            catch (PropertyException e)
            {
                throw new ConfigurationException("Unable to parse access control filename '" + jmxaccesssFile + "'");
            }

            try
            {
                _mbean.setAccessFile(jmxaccesssFile);
            }
            catch (IOException e)
            {
                _logger.warn("Unable to load access file:" + jmxaccesssFile);
            }

            try
            {
                _mbean.register();
            }
            catch (AMQException e)
            {
                _logger.warn("Unable to register user management MBean");
            }
        }
        catch (JMException e)
        {
            _logger.warn("User management disabled as unable to create MBean:" + e);
        }
    }
}
