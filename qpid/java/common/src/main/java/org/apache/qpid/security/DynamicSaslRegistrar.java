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

import java.security.Security;
import java.util.Map;
import java.util.TreeMap;

import javax.security.sasl.SaslClientFactory;

import org.apache.qpid.QpidConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicSaslRegistrar
{
    private static final Logger _logger = LoggerFactory.getLogger(DynamicSaslRegistrar.class);

    public static void registerSaslProviders()
    {
        Map<String, Class> factories = registerSaslClientFactories();
        if (factories.size() > 0)
        {
            if (Security.addProvider(new JCAProvider(factories)) == -1)
            {
                _logger.error("Unable to load custom SASL providers. Qpid custom SASL authenticators unavailable.");                
            }
            else
            {
                _logger.debug("Dynamic SASL provider added as a security provider");
            }
        }
    }

    private static Map<String, Class> registerSaslClientFactories()
    {
        TreeMap<String, Class> factoriesToRegister =
                new TreeMap<String, Class>();

        for (QpidConfig.SaslClientFactory factory: QpidConfig.get().getSaslClientFactories())
        {
            String className = factory.getFactoryClass();
            try
            {
                Class clazz = Class.forName(className);
                if (!(SaslClientFactory.class.isAssignableFrom(clazz)))
                {
                    _logger.debug("Class " + clazz + " does not implement " + SaslClientFactory.class + " - skipping");
                    continue;
                }
                factoriesToRegister.put(factory.getType(), clazz);
            }
            catch (Exception ex)
            {
                _logger.debug("Error instantiating SaslClientFactory calss " + className  + " - skipping");
            }
        }
        return factoriesToRegister;
    }


}
