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
package org.apache.qpid.security;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.QpidConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallbackHandlerRegistry
{
    private static final Logger _logger = LoggerFactory.getLogger(CallbackHandlerRegistry.class);
    private static CallbackHandlerRegistry _instance = new CallbackHandlerRegistry();

    private Map<String,Class> _mechanismToHandlerClassMap = new HashMap<String,Class>();

    private StringBuilder _mechanisms;

    public static CallbackHandlerRegistry getInstance()
    {
        return _instance;
    }

    public Class getCallbackHandlerClass(String mechanism)
    {
        return _mechanismToHandlerClassMap.get(mechanism);
    }

    public String getMechanisms()
    {
        return _mechanisms.toString();
    }

    private CallbackHandlerRegistry()
    {
        // first we register any Sasl client factories
        DynamicSaslRegistrar.registerSaslProviders();
        registerMechanisms();
    }

    private void registerMechanisms()
    {
        for (QpidConfig.SecurityMechanism  securityMechanism: QpidConfig.get().getSecurityMechanisms() )
        {
            Class clazz = null;
            try
            {
                clazz = Class.forName(securityMechanism.getHandler());
                if (!AMQPCallbackHandler.class.isAssignableFrom(clazz))
                {
                    _logger.debug("SASL provider " + clazz + " does not implement " + AMQPCallbackHandler.class +
                                 ". Skipping");
                    continue;
                }
                _mechanismToHandlerClassMap.put(securityMechanism.getType(), clazz);
                if (_mechanisms == null)
                {

                    _mechanisms = new StringBuilder();
                    _mechanisms.append(securityMechanism.getType());
                }
                else
                {
                    _mechanisms.append(" " + securityMechanism.getType());
                }
            }
            catch (ClassNotFoundException ex)
            {
                _logger.debug("Unable to load class " + securityMechanism.getHandler() + ". Skipping that SASL provider");
                continue;
            }
        }
    }
}
