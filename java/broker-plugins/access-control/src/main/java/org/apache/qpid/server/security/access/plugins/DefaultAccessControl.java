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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.File;

import javax.security.auth.Subject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.AccessControl;
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

    public DefaultAccessControl(String fileName)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Creating AccessControl instance using file: " + fileName);
        }
        File aclFile = new File(fileName);

        ConfigurationFile configFile = new PlainConfiguration(aclFile);
        _ruleSet = configFile.load();
    }

    DefaultAccessControl(RuleSet rs) throws ConfigurationException
    {
        _ruleSet = rs;
    }

    public Result getDefault()
    {
        return _ruleSet.getDefault();
    }

    /**
     * Object instance access authorisation.
     *
	 * Delegate to the {@link #authorise(Operation, ObjectType, ObjectProperties)} method, with
     * the operation set to ACCESS and no object properties.
	 */
    public Result access(ObjectType objectType, Object inetSocketAddress)
    {
        InetAddress addressOfClient = null;

        if(inetSocketAddress != null)
        {
            addressOfClient = ((InetSocketAddress) inetSocketAddress).getAddress();
        }

        return authoriseFromAddress(Operation.ACCESS, objectType, ObjectProperties.EMPTY, addressOfClient);
    }

    /**
     * Check if an operation is authorised by asking the  configuration object about the access
     * control rules granted to the current thread's {@link Subject}. If there is no current
     * user the plugin will abstain.
     */
    public Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        return authoriseFromAddress(operation, objectType, properties, null);
    }

    public Result authoriseFromAddress(Operation operation, ObjectType objectType, ObjectProperties properties, InetAddress addressOfClient)
    {
        final Subject subject = SecurityManager.getThreadSubject();
        // Abstain if there is no subject/principal associated with this thread
        if (subject == null  || subject.getPrincipals().size() == 0)
        {
            return Result.ABSTAIN;
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
