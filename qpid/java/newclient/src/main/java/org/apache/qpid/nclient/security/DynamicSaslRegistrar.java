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

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.security.sasl.SaslClientFactory;

import org.apache.log4j.Logger;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.QpidConstants;

public class DynamicSaslRegistrar
{
    private static final Logger _logger = Logger.getLogger(DynamicSaslRegistrar.class);

    public static void registerSaslProviders()
    {
    	Map<String, Class<? extends SaslClientFactory>> factories = parseProperties();
        if (factories.size() > 0)
        {
            Security.addProvider(new JCAProvider(factories));
            _logger.debug("Dynamic SASL provider added as a security provider");
        }
    }

    private static Map<String, Class<? extends SaslClientFactory>> parseProperties()
    {
        List<String> mechanisms = ClientConfiguration.get().getList(QpidConstants.AMQP_SECURITY_SASL_CLIENT_FACTORY_TYPES);
        TreeMap<String, Class<? extends SaslClientFactory>> factoriesToRegister =
                new TreeMap<String, Class<? extends SaslClientFactory>>();
        for (String mechanism: mechanisms)
        {
            String className = ClientConfiguration.get().getString(QpidConstants.AMQP_SECURITY_SASL_CLIENT_FACTORY + "_" + mechanism);
            try
            {
                Class<?> clazz = Class.forName(className);
                if (!(SaslClientFactory.class.isAssignableFrom(clazz)))
                {
                    _logger.error("Class " + clazz + " does not implement " + SaslClientFactory.class + " - skipping");
                    continue;
                }
                factoriesToRegister.put(mechanism, (Class<? extends SaslClientFactory>) clazz);
            }
            catch (Exception ex)
            {
                _logger.error("Error instantiating SaslClientFactory calss " + className  + " - skipping");
            }
        }
        return factoriesToRegister;
    }


}
