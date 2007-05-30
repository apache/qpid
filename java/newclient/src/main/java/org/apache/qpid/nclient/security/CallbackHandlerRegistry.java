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
package org.apache.qpid.nclient.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPConstants;

public class CallbackHandlerRegistry
{
    private static final Logger _logger = Logger.getLogger(CallbackHandlerRegistry.class);

    private static CallbackHandlerRegistry _instance = new CallbackHandlerRegistry();

    private Map<String,Class> _mechanismToHandlerClassMap = new HashMap<String,Class>();

    private String _mechanisms;

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
        return _mechanisms;
    }

    private CallbackHandlerRegistry()
    {
        // first we register any Sasl client factories
        DynamicSaslRegistrar.registerSaslProviders();
        parseProperties();
    }

    private void parseProperties()
    {
	String key = AMQPConstants.AMQP_SECURITY + "." + 
        AMQPConstants.AMQP_SECURITY_MECHANISMS + "." +
        AMQPConstants.AMQP_SECURITY_MECHANISM_HANDLER;
        
        int index = ClientConfiguration.get().getMaxIndex(key);                                       
        	
        for (int i=0; i<index+1;i++)
        {
            String mechanism = ClientConfiguration.get().getString(key + "(" + i + ")[@type]");
            String className = ClientConfiguration.get().getString(key + "(" + i + ")" );
            Class clazz = null;
            try
            {
                clazz = Class.forName(className);
                if (!AMQPCallbackHandler.class.isAssignableFrom(clazz))
                {
                    _logger.warn("SASL provider " + clazz + " does not implement " + AMQPCallbackHandler.class +
                                 ". Skipping");
                    continue;
                }
                _mechanismToHandlerClassMap.put(mechanism, clazz);
                if (_mechanisms == null)
                {
                    _mechanisms = mechanism;
                }
                else
                {
                    // one time cost
                    _mechanisms = _mechanisms + " " + mechanism;
                }
            }
            catch (ClassNotFoundException ex)
            {
                _logger.warn("Unable to load class " + className + ". Skipping that SASL provider");
                continue;
            }
        }
    }
}
