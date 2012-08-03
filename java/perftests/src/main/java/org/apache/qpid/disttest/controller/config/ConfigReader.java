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
package org.apache.qpid.disttest.controller.config;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;

import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.json.PropertyValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ConfigReader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigReader.class);

    public Config getConfigFromFile(String fileName) throws FileNotFoundException
    {
        Reader reader = getConfigReader(fileName);

        Config config = readConfig(reader);
        return config;
    }

    protected Reader getConfigReader(String fileName) throws FileNotFoundException
    {
        Reader reader = null;
        if (fileName.endsWith(".js"))
        {
            LOGGER.info("Evaluating javascript:" + fileName);
            reader = new StringReader(new JavaScriptConfigEvaluator().evaluateJavaScript(fileName));
        }
        else
        {
            LOGGER.info("Loading JSON:" + fileName);
            reader = new FileReader(fileName);
        }
        return reader;
    }

    public Config readConfig(Reader reader)
    {
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(PropertyValue.class, new PropertyValueAdapter())
            .create();
        Config config = gson.fromJson(reader, Config.class);
        return config;
    }

}
