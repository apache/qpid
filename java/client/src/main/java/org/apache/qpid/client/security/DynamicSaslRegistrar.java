/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.client.security;

import org.apache.log4j.Logger;

import javax.security.sasl.SaslClientFactory;
import java.io.*;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.security.Security;

public class DynamicSaslRegistrar
{
    private static final String FILE_PROPERTY = "amq.dynamicsaslregistrar.properties";

    private static final Logger _logger = Logger.getLogger(DynamicSaslRegistrar.class);

    public static void registerSaslProviders()
    {
        InputStream is = openPropertiesInputStream();
        try
        {
            Properties props = new Properties();
            props.load(is);
            Map<String, Class<? extends SaslClientFactory>> factories = parseProperties(props);
            if (factories.size() > 0)
            {
                Security.addProvider(new JCAProvider(factories));
                _logger.debug("Dynamic SASL provider added as a security provider");
            }
        }
        catch (IOException e)
        {
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
    }

    private static InputStream openPropertiesInputStream()
    {
        String filename = System.getProperty(FILE_PROPERTY);
        boolean useDefault = true;
        InputStream is = null;
        if (filename != null)
        {
            try
            {
                is = new BufferedInputStream(new FileInputStream(new File(filename)));
                useDefault = false;
            }
            catch (FileNotFoundException e)
            {
                _logger.error("Unable to read from file " + filename + ": " + e, e);
            }
        }

        if (useDefault)
        {
            is = CallbackHandlerRegistry.class.getResourceAsStream("DynamicSaslRegistrar.properties");
        }

        return is;
    }

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
