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
package org.apache.qpid.server.security.access.plugins;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.AccessController;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.config.ConfigurationFile;
import org.apache.qpid.server.security.access.config.PlainConfiguration;
import org.apache.qpid.server.security.access.config.RuleSet;

public class DefaultAccessControl implements AccessControl
{
    private static final Logger _logger = Logger.getLogger(DefaultAccessControl.class);

    private RuleSet _ruleSet;
    private File _aclFile;
    private final EventLoggerProvider _eventLogger;

    public DefaultAccessControl(String fileName, final EventLoggerProvider eventLogger)
    {
        _eventLogger = eventLogger;
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Creating AccessControl instance using file: " + fileName);
        }

        _aclFile = new File(fileName);
    }

    DefaultAccessControl(RuleSet rs)
    {
        _ruleSet = rs;
        _eventLogger = rs;
    }

    public void open()
    {
        if(_aclFile != null)
        {
            if (!validate())
            {
                throw new IllegalConfigurationException("ACL file '" + _aclFile + "' is not found");
            }

            ConfigurationFile configFile = new PlainConfiguration(_aclFile, _eventLogger);
            _ruleSet = configFile.load();
        }
    }

    @Override
    public boolean validate()
    {
        return _aclFile.exists();
    }

    @Override
    public void close()
    {
        //no-op
    }

    @Override
    public void onDelete()
    {
        //no-op
    }

    @Override
    public void onCreate()
    {
        if(_aclFile != null)
        {
            //verify it exists
            if (!validate())
            {
                throw new IllegalConfigurationException("ACL file '" + _aclFile + "' is not found");
            }

            //verify it is parsable
            new PlainConfiguration(_aclFile, _eventLogger).load();
        }
    }

    public Result getDefault()
    {
        return _ruleSet.getDefault();
    }

    /**
     * Check if an operation is authorised by asking the  configuration object about the access
     * control rules granted to the current thread's {@link Subject}. If there is no current
     * user the plugin will abstain.
     */
    public Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        InetAddress addressOfClient = null;
        final Subject subject = Subject.getSubject(AccessController.getContext());

        // Abstain if there is no subject/principal associated with this thread
        if (subject == null  || subject.getPrincipals().size() == 0)
        {
            return Result.ABSTAIN;
        }

        Set<ConnectionPrincipal> principals = subject.getPrincipals(ConnectionPrincipal.class);
        if(!principals.isEmpty())
        {
            SocketAddress address = principals.iterator().next().getConnection().getRemoteAddress();
            if(address instanceof InetSocketAddress)
            {
                addressOfClient = ((InetSocketAddress) address).getAddress();
            }
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Checking " + operation + " " + objectType + " " + ObjectUtils.defaultIfNull(addressOfClient, ""));
        }

        try
        {
            return  _ruleSet.check(subject, operation, objectType, properties, addressOfClient);
        }
        catch(Exception e)
        {
            _logger.error("Unable to check " + operation + " " + objectType + " " + ObjectUtils.defaultIfNull(addressOfClient, ""), e);
            return Result.DENIED;
        }
    }

}
