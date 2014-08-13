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
package org.apache.qpid.disttest;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;

public class AbstractRunner
{
    public static final String JNDI_CONFIG_PROP = "jndi-config";
    public static final String JNDI_CONFIG_DEFAULT = "perftests-jndi.properties";

    private Map<String,String> _cliOptions = new HashMap<String, String>();
    {
        getCliOptions().put(JNDI_CONFIG_PROP, JNDI_CONFIG_DEFAULT);
    }

    protected Context getContext()
    {
        Context context = null;

        try(FileInputStream inStream = new FileInputStream(getJndiConfig()))
        {
            final Properties properties = new Properties();
            properties.load(inStream);
            context = new InitialContext(properties);
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Exception while loading JNDI properties", e);
        }

        return context;
    }

    public void parseArgumentsIntoConfig(String[] args)
    {
        ArgumentParser argumentParser = new ArgumentParser();
        argumentParser.parseArgumentsIntoConfig(getCliOptions(), args);
    }

    protected String getJndiConfig()
    {
        return getCliOptions().get(AbstractRunner.JNDI_CONFIG_PROP);
    }

    protected Map<String,String> getCliOptions()
    {
        return _cliOptions;
    }
}
