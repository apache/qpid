/*
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
package org.apache.qpid.systest.disttest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.qpid.disttest.controller.config.Config;
import org.apache.qpid.disttest.controller.config.ConfigReader;

public class ConfigFileTestHelper
{
    public static Reader getConfigFileReader(Class<?> testClass, String resourceName)
    {
        InputStream inputStream = testClass.getResourceAsStream(resourceName);
        if(inputStream == null)
        {
            throw new RuntimeException("Can't find resource " + resourceName + " using classloader of class " + testClass);
        }
        Reader reader = new InputStreamReader(inputStream);
        return reader;
    }

    public static Config getConfigFromResource(Class<?> testClass, String resourceName)
    {
        ConfigReader configReader = new ConfigReader();
        Config config = configReader.readConfig(getConfigFileReader(testClass, resourceName));
        return config;
    }
}
