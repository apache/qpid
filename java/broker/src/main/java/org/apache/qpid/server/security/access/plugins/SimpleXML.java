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

package org.apache.qpid.server.security.access.plugins;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.BasicPublishBody;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.access.ACLPlugin;
import org.apache.qpid.server.security.access.AccessResult;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.security.access.PrincipalPermissions;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This uses the default
 */
public class SimpleXML implements ACLPlugin
{
    private Map<String, PrincipalPermissions> _users;
    private final AccessResult GRANTED = new AccessResult(this, AccessResult.AccessStatus.GRANTED);

    public SimpleXML()
    {
        _users = new ConcurrentHashMap<String, PrincipalPermissions>();
    }

    public void setConfiguaration(Configuration config)
    {
        processConfig(config);
    }

    private void processConfig(Configuration config)
    {
        processPublish(config);

        processConsume(config);

        processCreate(config);
    }

    /**
     * Publish format takes Exchange + Routing Key Pairs
     * 
     * @param config
     *            XML Configuration
     */
    private void processPublish(Configuration config)
    {
        Configuration publishConfig = config.subset("security.access_control_list.publish");

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

    private void processConsume(Configuration config)
    {
        Configuration consumeConfig = config.subset("security.access_control_list.consume");

        // Process queue limited users
        int queueCount = 0;
        Configuration queueConfig = consumeConfig.subset("queues.queue(" + queueCount + ")");

        while (!queueConfig.isEmpty())
        {
            // Get queue Name
            AMQShortString queueName = new AMQShortString(queueConfig.getString("name"));
            // if there is no name then there may be a temporary element
            boolean temporary = queueConfig.containsKey("temporary");
            boolean ownQueues = queueConfig.containsKey("own_queues");

            // Process permissions for this queue
            String[] users = queueConfig.getStringArray("users.user");
            for (String user : users)
            {
                grant(Permission.CONSUME, user, queueName, temporary, ownQueues);
            }

            // See if we have another config
            queueCount++;
            queueConfig = consumeConfig.subset("queues.queue(" + queueCount + ")");
        }

        // Process users that have full consume permission
        String[] users = consumeConfig.getStringArray("users.user");

        for (String user : users)
        {
            grant(Permission.CONSUME, user);
        }
    }

    private void processCreate(Configuration config)
    {
        Configuration createConfig = config.subset("security.access_control_list.create");

        // Process create permissions for queue creation
        int queueCount = 0;
        Configuration queueConfig = createConfig.subset("queues.queue(" + queueCount + ")");

        while (!queueConfig.isEmpty())
        {
            // Get queue Name
            AMQShortString queueName = new AMQShortString(queueConfig.getString("name"));

            // if there is no name then there may be a temporary element
            boolean temporary = queueConfig.containsKey("temporary");

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
                    grant(Permission.CREATEEXCHANGE, user, exchange);
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

    public String getPluginName()
    {
        return "Simple";
    }

    @Override
    public boolean authoriseBind(AMQProtocolSession session, Exchange exch, AMQQueue queue, AMQShortString routingKey)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.BIND, null, exch, queue, routingKey);
        }
    }

    @Override
    public boolean authoriseConnect(AMQProtocolSession session, VirtualHost virtualHost)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.ACCESS);
        }
    }

    @Override
    public boolean authoriseConsume(AMQProtocolSession session, boolean noAck, AMQQueue queue)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.CONSUME, queue);
        }
    }

    @Override
    public boolean authoriseConsume(AMQProtocolSession session, boolean exclusive, boolean noAck, boolean noLocal,
            boolean nowait, AMQQueue queue)
    {
        return authoriseConsume(session, noAck, queue);
    }

    @Override
    public boolean authoriseCreateExchange(AMQProtocolSession session, boolean autoDelete, boolean durable,
            AMQShortString exchangeName, boolean internal, boolean nowait, boolean passive, AMQShortString exchangeType)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.CREATEEXCHANGE, exchangeName);
        }
    }

    @Override
    public boolean authoriseCreateQueue(AMQProtocolSession session, boolean autoDelete, boolean durable, boolean exclusive,
            boolean nowait, boolean passive, AMQShortString queue)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.CREATEQUEUE, autoDelete, queue);
        }
    }

    @Override
    public boolean authoriseDelete(AMQProtocolSession session, AMQQueue queue)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.DELETE);
        }
    }

    @Override
    public boolean authoriseDelete(AMQProtocolSession session, Exchange exchange)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.DELETE);
        }
    }

    @Override
    public boolean authorisePublish(AMQProtocolSession session, boolean immediate, boolean mandatory,
            AMQShortString routingKey, Exchange e)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.PUBLISH, e, routingKey);
        }
    }

    @Override
    public boolean authorisePurge(AMQProtocolSession session, AMQQueue queue)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.PURGE);
        }
    }

    @Override
    public boolean authoriseUnbind(AMQProtocolSession session, Exchange exch, AMQShortString routingKey, AMQQueue queue)
    {
        PrincipalPermissions principalPermissions = _users.get(session.getAuthorizedID().getName());
        if (principalPermissions == null)
        {
            return false;
        }
        else
        {
            return principalPermissions.authorise(Permission.UNBIND);
        }
    }
}
