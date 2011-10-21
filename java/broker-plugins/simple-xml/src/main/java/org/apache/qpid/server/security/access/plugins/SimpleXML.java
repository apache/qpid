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
 */
package org.apache.qpid.server.security.access.plugins;

import static org.apache.qpid.server.security.access.ObjectProperties.Property.*;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.security.AbstractPlugin;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SecurityPluginFactory;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.config.PrincipalPermissions;
import org.apache.qpid.server.security.access.config.PrincipalPermissions.Permission;

/**
 * This uses the default
 */
public class SimpleXML extends AbstractPlugin
{
    public static final Logger _logger = Logger.getLogger(SimpleXML.class);

    private Map<String, PrincipalPermissions> _users;
	
    public static final SecurityPluginFactory<SimpleXML> FACTORY = new SecurityPluginFactory<SimpleXML>()
    {
        public SimpleXML newInstance(ConfigurationPlugin config) throws ConfigurationException
        {
            SimpleXMLConfiguration configuration = config.getConfiguration(SimpleXMLConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                return null;
            }

            SimpleXML plugin = new SimpleXML();
            plugin.configure(configuration);
            return plugin;
        }

        public String getPluginName()
        {
            return SimpleXML.class.getName();
        }

        public Class<SimpleXML> getPluginClass()
        {
            return SimpleXML.class;
        }
    };

    public void configure(ConfigurationPlugin config)
    {
        super.configure(config);

        SimpleXMLConfiguration configuration = (SimpleXMLConfiguration) _config;
        
        _users = new ConcurrentHashMap<String, PrincipalPermissions>();

        processConfig(configuration.getConfiguration());
    }

    private void processConfig(Configuration config)
    {
        processPublish(config);
        processConsume(config);
        processCreate(config);
        processAccess(config);
    }

    /**
	 * @param config XML Configuration
     */
    private void processAccess(Configuration config)
    {
        Configuration accessConfig = config.subset("access");
        
        if (accessConfig.isEmpty())
        {
            //there is no access configuration to process
            return;
        }
        
        // Process users that have full access permission
        String[] users = accessConfig.getStringArray("users.user");
        
        for (String user : users)
        {
            grant(Permission.ACCESS, user);
        }
    }
    
    /**
	 * Publish format takes Exchange + Routing Key Pairs
     *
     * @param config XML Configuration
     */
    private void processPublish(Configuration config)
    {
        Configuration publishConfig = config.subset("publish");

        // Process users that have full publish permission
        String[] users = publishConfig.getStringArray("users.user");

        for (String user : users)
        {
            grant(Permission.PUBLISH, user);
        }

        // Process exchange limited users
        int exchangeCount = 0;
        Configuration exchangeConfig = publishConfig.subset("exchanges.exchange(" + exchangeCount + ")");

        while (!exchangeConfig.isEmpty())
        {
            // Get Exchange Name
            AMQShortString exchangeName = new AMQShortString(exchangeConfig.getString("name"));

            // Get Routing Keys
            int keyCount = 0;
            Configuration routingkeyConfig = exchangeConfig.subset("routing_keys.routing_key(" + keyCount + ")");

            while (!routingkeyConfig.isEmpty())
            {
                // Get RoutingKey Value
                AMQShortString routingKeyValue = new AMQShortString(routingkeyConfig.getString("value"));

                // Apply Exchange + RoutingKey permissions to Users
                users = routingkeyConfig.getStringArray("users.user");
                for (String user : users)
                {
                    grant(Permission.PUBLISH, user, exchangeName, routingKeyValue);
                }

                // Apply permissions to Groups

                // Check for more configs
                keyCount++;
                routingkeyConfig = exchangeConfig.subset("routing_keys.routing_key(" + keyCount + ")");
            }

            // Apply Exchange wide permissions to Users
            users = exchangeConfig.getStringArray("exchange(" + exchangeCount + ").users.user");

            for (String user : users)
            {
                grant(Permission.PUBLISH, user, exchangeName);
            }

            // Apply permissions to Groups
            exchangeCount++;
            exchangeConfig = publishConfig.subset("exchanges.exchange(" + exchangeCount + ")");
        }
    }

    private void grant(Permission permission, String user, Object... parameters)
    {
        PrincipalPermissions permissions = _users.get(user);

        if (permissions == null)
        {
            permissions = new PrincipalPermissions(user);
        }

        _users.put(user, permissions);
        permissions.grant(permission, parameters);
    }

	/**
	 * @param config XML Configuration
     */
    private void processConsume(Configuration config)
    {
        boolean temporary = false;
        Configuration tempConfig = null;
        Configuration consumeConfig = config.subset("consume");

        tempConfig = consumeConfig.subset("queues.temporary(0)");
        if (tempConfig != null)
        {
            temporary = true;
        }

        //Permission all users who have rights to temp queues and topics
        if (tempConfig != null && !tempConfig.isEmpty())
        {
            String[] tempUsers = tempConfig.getStringArray("users.user");
            for (String user : tempUsers)
            {
                grant(Permission.CONSUME, user, temporary);
            }
        }

        // Process queue limited users
        int queueCount = 0;
        Configuration queueConfig = consumeConfig.subset("queues.queue(" + queueCount + ")");

        while (!queueConfig.isEmpty())
        {
            // Get queue Name
            AMQShortString queueName = new AMQShortString(queueConfig.getString("name"));
            // if there is no name then there may be a temporary element

            boolean ownQueues = queueConfig.containsKey("own_queues");

            // Process permissions for this queue
            String[] users = queueConfig.getStringArray("users.user");
            for (String user : users)
            {
                grant(Permission.CONSUME, user, queueName, ownQueues);
            }

            // See if we have another config
            queueCount++;
            queueConfig = consumeConfig.subset("queues.queue(" + queueCount + ")");
        }

        // Process users that have full consume permission
        String[] users = consumeConfig.getStringArray("users.user");

        for (String user : users)
        {
            //NOTE: this call does not appear to do anything inside the grant section for consume
            grant(Permission.CONSUME, user);
        }
    }

	/**
	 * @param config XML Configuration
     */
    private void processCreate(Configuration config)
    {
        boolean temporary = false;
        Configuration tempConfig = null;

        Configuration createConfig = config.subset("create");

        tempConfig = createConfig.subset("queues.temporary(0)");
        if (tempConfig != null)
        {
            temporary = true;
        }

        //Permission all users who have rights to temp queues and topics
        if (tempConfig != null && !tempConfig.isEmpty())
        {
            String[] tempUsers = tempConfig.getStringArray("users.user");
            for (String user : tempUsers)
            {
                grant(Permission.CREATEQUEUE, user, temporary);
            }
        }
        // Process create permissions for queue creation
        int queueCount = 0;
        Configuration queueConfig = createConfig.subset("queues.queue(" + queueCount + ")");

        while (!queueConfig.isEmpty())
        {
            // Get queue Name
            AMQShortString queueName = new AMQShortString(queueConfig.getString("name"));

            int exchangeCount = 0;
            Configuration exchangeConfig = queueConfig.subset("exchanges.exchange(" + exchangeCount + ")");

            while (!exchangeConfig.isEmpty())
            {

                AMQShortString exchange = new AMQShortString(exchangeConfig.getString("name"));
                AMQShortString routingKey = new AMQShortString(exchangeConfig.getString("routing_key"));
               
                // Process permissions for this queue
                String[] users = exchangeConfig.getStringArray("users.user");
                for (String user : users)
                {
                    //This is broken as the user name is not stored
                    grant(Permission.CREATEEXCHANGE, user, exchange);

                    //This call could be cleaned up as temporary is now being set earlier (above) 
                    grant(Permission.CREATEQUEUE, user, temporary, (queueName.equals("") ? null : queueName), (exchange
                            .equals("") ? null : exchange), (routingKey.equals("") ? null : routingKey));
                }

                // See if we have another config
                exchangeCount++;
                exchangeConfig = queueConfig.subset("exchanges.exchange(" + exchangeCount + ")");
            }

            // Process users that are not bound to an exchange
            String[] users = queueConfig.getStringArray("users.user");

            for (String user : users)
            {
                grant(Permission.CREATEQUEUE, user, temporary, queueName);
            }

            // See if we have another config
            queueCount++;
            queueConfig = createConfig.subset("queues.queue(" + queueCount + ")");
        }

        // Process create permissions for exchange creation
        int exchangeCount = 0;
        Configuration exchangeConfig = createConfig.subset("exchanges.exchange(" + exchangeCount + ")");

        while (!exchangeConfig.isEmpty())
        {
            AMQShortString exchange = new AMQShortString(exchangeConfig.getString("name"));
            AMQShortString clazz = new AMQShortString(exchangeConfig.getString("class"));

            // Process permissions for this queue
            String[] users = exchangeConfig.getStringArray("users.user");
            for (String user : users)
            {
                //And this is broken too 
                grant(Permission.CREATEEXCHANGE, user, exchange, clazz);
            }

            // See if we have another config
            exchangeCount++;
            exchangeConfig = queueConfig.subset("exchanges.exchange(" + exchangeCount + ")");
        }

        // Process users that have full create permission
        String[] users = createConfig.getStringArray("users.user");

        for (String user : users)
        {
            grant(Permission.CREATEEXCHANGE, user);
            grant(Permission.CREATEQUEUE, user);
        }
    }

    public Result access(ObjectType objectType, Object instance)
    {
        Principal principal = SecurityManager.getThreadPrincipal();
        if (principal == null)
        {
            return getDefault(); // Default if there is no user associated with the thread
        }
        PrincipalPermissions principalPermissions = _users.get(principal.getName());
        if (principalPermissions == null)
        {
            return Result.DENIED;
        }
        
        // Authorise object access
        if (objectType == ObjectType.VIRTUALHOST)
        {
            return principalPermissions.authorise(Permission.ACCESS);
        }
        
        // Default
		return getDefault();
    }

    public Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        Principal principal = SecurityManager.getThreadPrincipal();
        if (principal == null)
        {
            return getDefault(); // Default if there is no user associated with the thread
        }
        PrincipalPermissions principalPermissions = _users.get(principal.getName());
        if (principalPermissions == null)
        {
            return Result.DENIED;
        }
        
        // Authorise operation
        switch (operation)
        {
        case CONSUME:
            return principalPermissions.authorise(Permission.CONSUME, properties.get(NAME), properties.get(AUTO_DELETE), properties.get(OWNER));
        case PUBLISH:
            return principalPermissions.authorise(Permission.PUBLISH, properties.get(NAME), properties.get(ROUTING_KEY));
        case CREATE:
            if (objectType == ObjectType.EXCHANGE)
            {
                return principalPermissions.authorise(Permission.CREATEEXCHANGE, properties.get(NAME));
            }
            else if (objectType == ObjectType.QUEUE)
            {
                return principalPermissions.authorise(Permission.CREATEQUEUE, properties.get(AUTO_DELETE), properties.get(NAME));
            }
        case ACCESS:
            return principalPermissions.authorise(Permission.ACCESS);
        case BIND:
            return principalPermissions.authorise(Permission.BIND, null, properties.get(NAME), properties.get(QUEUE_NAME), properties.get(ROUTING_KEY));
        case UNBIND:
            return principalPermissions.authorise(Permission.UNBIND);
        case DELETE:
            return principalPermissions.authorise(Permission.DELETE);
        case PURGE:
            return principalPermissions.authorise(Permission.PURGE);
        }
        
        // Default
		return getDefault();
    }
}
