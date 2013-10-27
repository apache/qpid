/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.management.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class DojoHelper
{
    private static final Logger _logger = LoggerFactory.getLogger(DojoHelper.class);

    public static final String VERSION_FILE = "dojoconfig.properties";
    public static final String DOJO_VERSION_PROPERTY = "dojo-version";
    public static final String DOJO_PATH_PROPERTY = "dojo-path";
    public static final String DIJIT_PATH_PROPERTY = "dijit-path";
    public static final String DOJOX_PATH_PROPERTY = "dojox-path";

    private static String _version = "undefined";
    private static String _dojoPath = "/dojo-undefined/dojo";
    private static String _dijitPath = "/dojo-undefined/dijit";
    private static String _dojoxPath = "/dojo-undefined/dojox";

    // Loads the value from the properties file.
    static
    {
        Properties props = new Properties();

        try
        {
            InputStream propertyStream = DojoHelper.class.getClassLoader().getResourceAsStream(VERSION_FILE);
            if (propertyStream == null)
            {
                _logger.warn("Unable to find resource " + VERSION_FILE + " from classloader");
            }
            else
            {
                try
                {
                    props.load(propertyStream);
                }
                finally
                {
                    try
                    {
                        propertyStream.close();
                    }
                    catch (IOException e)
                    {
                        _logger.warn("Exception closing InputStream for " + VERSION_FILE + " resource:", e);
                    }
                }

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dumping Dojo Config:");
                    for (Map.Entry<Object, Object> entry : props.entrySet())
                    {
                        _logger.debug("Property: " + entry.getKey() + " Value: " + entry.getValue());
                    }

                    _logger.debug("End of property dump");
                }

                _version = props.getProperty(DOJO_VERSION_PROPERTY, _version);
                _dojoPath = props.getProperty(DOJO_PATH_PROPERTY, _dojoPath);
                _dijitPath = props.getProperty(DIJIT_PATH_PROPERTY, _dijitPath);
                _dojoxPath = props.getProperty(DOJOX_PATH_PROPERTY, _dojoxPath);
            }
        }
        catch (IOException e)
        {
            // Log a warning about this and leave the values initialized to unknown.
            _logger.error("Exception loading " + VERSION_FILE + " resource:", e);
        }
    }

    public static String getDojoVersion()
    {
        return _version;
    }

    public static String getDojoPath()
    {
        return _dojoPath;
    }

    public static String getDijitPath()
    {
        return _dijitPath;
    }

    public static String getDojoxPath()
    {
        return _dojoxPath;
    }
}
