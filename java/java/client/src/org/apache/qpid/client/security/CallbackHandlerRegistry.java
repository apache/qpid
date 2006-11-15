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

import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

public class CallbackHandlerRegistry
{
    private static final String FILE_PROPERTY = "amq.callbackhandler.properties";

    private static final Logger _logger = Logger.getLogger(CallbackHandlerRegistry.class);

    private static CallbackHandlerRegistry _instance = new CallbackHandlerRegistry();

    private Map _mechanismToHandlerClassMap = new HashMap();

    private String _mechanisms;

    public static CallbackHandlerRegistry getInstance()
    {
        return _instance;        
    }

    public Class getCallbackHandlerClass(String mechanism)
    {
        return (Class) _mechanismToHandlerClassMap.get(mechanism);
    }

    public String getMechanisms()
    {
        return _mechanisms;
    }

    private CallbackHandlerRegistry()
    {
        // first we register any Sasl client factories
        DynamicSaslRegistrar.registerSaslProviders();

        InputStream is = openPropertiesInputStream();
        try
        {
            Properties props = new Properties();
            props.load(is);
            parseProperties(props);
            _logger.info("Available SASL mechanisms: " + _mechanisms);
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

    private InputStream openPropertiesInputStream()
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
            is = CallbackHandlerRegistry.class.getResourceAsStream("CallbackHandlerRegistry.properties");
        }

        return is;
    }

    private void parseProperties(Properties props)
    {
        Enumeration e = props.propertyNames();
        while (e.hasMoreElements())
        {
            String propertyName = (String) e.nextElement();
            int period = propertyName.indexOf(".");
            if (period < 0)
            {
                _logger.warn("Unable to parse property " + propertyName + " when configuring SASL providers");
                continue;
            }
            String mechanism = propertyName.substring(period + 1);
            String className = props.getProperty(propertyName);
            Class clazz = null;
            try
            {
                clazz = Class.forName(className);
                if (!AMQCallbackHandler.class.isAssignableFrom(clazz))
                {
                    _logger.warn("SASL provider " + clazz + " does not implement " + AMQCallbackHandler.class +
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
