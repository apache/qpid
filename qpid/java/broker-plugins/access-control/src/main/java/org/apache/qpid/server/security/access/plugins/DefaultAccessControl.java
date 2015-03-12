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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.security.AccessController;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.commons.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger _logger = LoggerFactory.getLogger(DefaultAccessControl.class);
    private final String _fileName;

    private RuleSet _ruleSet;
    private final EventLoggerProvider _eventLogger;

    public DefaultAccessControl(String name, final EventLoggerProvider eventLogger)
    {
        _fileName = name;
        _eventLogger = eventLogger;
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Creating AccessControl instance");
        }
    }

    DefaultAccessControl(RuleSet rs)
    {
        _fileName = null;
        _ruleSet = rs;
        _eventLogger = rs;
    }

    public void open()
    {
        if(_fileName != null)
        {
            ConfigurationFile configFile = new PlainConfiguration(_fileName, _eventLogger);
            _ruleSet = configFile.load(getReaderFromURLString(_fileName));
        }
    }

    @Override
    public boolean validate()
    {
        try
        {
            getReaderFromURLString(_fileName);
            return true;
        }
        catch(IllegalConfigurationException e)
        {
            return false;
        }
    }


    private static Reader getReaderFromURLString(String urlString)
    {
        try
        {
            URL url;

            try
            {
                url = new URL(urlString);
            }
            catch (MalformedURLException e)
            {
                File file = new File(urlString);
                try
                {
                    url = file.toURI().toURL();
                }
                catch (MalformedURLException notAFile)
                {
                    throw new IllegalConfigurationException("Cannot convert " + urlString + " to a readable resource");
                }

            }
            return new InputStreamReader(url.openStream());
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot convert " + urlString + " to a readable resource");
        }
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
        if(_fileName != null)
        {
            //verify it is parsable
            new PlainConfiguration(_fileName, _eventLogger).load(getReaderFromURLString(_fileName));
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
