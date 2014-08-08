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
package org.apache.qpid.client.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.util.FileUtils;

import javax.security.sasl.SaslClientFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * DynamicSaslRegistrar provides a collection of helper methods for reading a configuration file that contains a mapping
 * from SASL mechanism names to implementing client factory class names and registering a security provider with the
 * Java runtime system, that uses the configured client factory implementations.
 * <p>
 * The sasl configuration should be specified in a properties file, refered to by the System property
 * "amp.dynamicsaslregistrar.properties". The format of the properties file is:
 * <p>
 * <pre>
 * mechanism=fully.qualified.class.name
 * </pre>
 * <p>
 * Where mechanism is an IANA-registered mechanism name and the fully qualified class name refers to a class that
 * implements javax.security.sasl.SaslClientFactory and provides the specified mechanism.
 */
public class DynamicSaslRegistrar
{
    private static final Logger _logger = LoggerFactory.getLogger(DynamicSaslRegistrar.class);

    /** The name of the system property that holds the name of the SASL configuration properties. */
    private static final String FILE_PROPERTY = "amq.dynamicsaslregistrar.properties";

    /** The default name of the SASL properties file resource. */
    public static final String DEFAULT_RESOURCE_NAME = "org/apache/qpid/client/security/DynamicSaslRegistrar.properties";

    private DynamicSaslRegistrar()
    {
    }

    /** Reads the properties file, and creates a dynamic security provider to register the SASL implementations with. */
    public static ProviderRegistrationResult registerSaslProviders()
    {
        _logger.debug("public static void registerSaslProviders(): called");
        ProviderRegistrationResult result = ProviderRegistrationResult.FAILED;
        // Open the SASL properties file, using the default name is one is not specified.
        String filename = System.getProperty(FILE_PROPERTY);
        InputStream is =
            FileUtils.openFileOrDefaultResource(filename, DEFAULT_RESOURCE_NAME,
                DynamicSaslRegistrar.class.getClassLoader());

        try
        {
            Properties props = new Properties();
            props.load(is);

            _logger.debug("props = " + props);

            Map<String, Class<? extends SaslClientFactory>> factories = parseProperties(props);

            if (factories.size() > 0)
            {
                // Ensure we are used before the defaults
                JCAProvider qpidProvider = new JCAProvider(factories);
                if (Security.insertProviderAt(qpidProvider, 1) == -1)
                {
                    Provider registeredProvider = findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME);
                    if (registeredProvider == null)
                    {
                        result = ProviderRegistrationResult.FAILED;
                        _logger.error("Unable to load custom SASL providers.");
                    }
                    else if (registeredProvider.equals(qpidProvider))
                    {
                        result = ProviderRegistrationResult.EQUAL_ALREADY_REGISTERED;
                        _logger.debug("Custom SASL provider is already registered with equal properties.");
                    }
                    else
                    {
                        result = ProviderRegistrationResult.DIFFERENT_ALREADY_REGISTERED;
                        _logger.warn("Custom SASL provider was already registered with different properties.");
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Custom SASL provider " + registeredProvider + " properties: " + new HashMap<Object, Object>(registeredProvider));
                        }
                    }
                }
                else
                {
                    result = ProviderRegistrationResult.SUCCEEDED;
                    _logger.info("Additional SASL providers successfully registered.");
                }
            }
            else
            {
                result = ProviderRegistrationResult.NO_SASL_FACTORIES;
                _logger.warn("No additional SASL factories found to register.");
            }
        }
        catch (IOException e)
        {
            result = ProviderRegistrationResult.FAILED;
            _logger.error("Error reading properties: " + e, e);
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();

                }
                catch (IOException e)
                {
                    _logger.error("Unable to close properties stream: " + e, e);
                }
            }
        }
        return result;
    }

    static Provider findProvider(String name)
    {
        Provider[] providers = Security.getProviders();
        Provider registeredProvider = null;
        for (Provider provider : providers)
        {
            if (name.equals(provider.getName()))
            {
                registeredProvider = provider;
                break;
            }
        }
        return registeredProvider;
    }

    /**
     * Parses the specified properties as a mapping from IANA registered SASL mechanism names to implementing client
     * factories. If the client factories cannot be instantiated or do not implement SaslClientFactory then the
     * properties refering to them are ignored.
     *
     * @param props The properties to scan for Sasl client factory implementations.
     *
     * @return A map from SASL mechanism names to implementing client factory classes.
     *
     * @todo Why tree map here? Do really want mechanisms in alphabetical order? Seems more likely that the declared
     * order of the mechanisms is intended to be preserved, so that they are registered in the declared order of
     * preference. Consider LinkedHashMap instead.
     */
    private static Map<String, Class<? extends SaslClientFactory>> parseProperties(Properties props)
    {
        Enumeration e = props.propertyNames();

        TreeMap<String, Class<? extends SaslClientFactory>> factoriesToRegister =
            new TreeMap<String, Class<? extends SaslClientFactory>>();

        while (e.hasMoreElements())
        {
            String mechanism = (String) e.nextElement();
            String className = props.getProperty(mechanism);
            try
            {
                Class<?> clazz = Class.forName(className);
                if (!(SaslClientFactory.class.isAssignableFrom(clazz)))
                {
                    _logger.error("Class " + clazz + " does not implement " + SaslClientFactory.class + " - skipping");

                    continue;
                }

                _logger.debug("Found class "+ clazz.getName() +" for mechanism "+mechanism);
                factoriesToRegister.put(mechanism, (Class<? extends SaslClientFactory>) clazz);
            }
            catch (Exception ex)
            {
                _logger.error("Error instantiating SaslClientFactory class " + className + " - skipping");
            }
        }

        return factoriesToRegister;
    }

    public static enum ProviderRegistrationResult
    {
        SUCCEEDED,
        EQUAL_ALREADY_REGISTERED,
        DIFFERENT_ALREADY_REGISTERED,
        NO_SASL_FACTORIES,
        FAILED;
    }
}
