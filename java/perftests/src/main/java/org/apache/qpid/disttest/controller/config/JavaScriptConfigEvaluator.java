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
package org.apache.qpid.disttest.controller.config;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.qpid.disttest.DistributedTestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class to load and evaluate JavaScript configuration, producing a JSON string.
 */
public class JavaScriptConfigEvaluator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptConfigEvaluator.class);

    public static final String TEST_CONFIG_VARIABLE_NAME = "jsonObject";

    public String evaluateJavaScript(String fileName) throws IOException
    {
        FileReader fileReader = null;
        try
        {
            fileReader = new FileReader(fileName);
            String result = evaluateJavaScript(fileReader);
            LOGGER.debug("Evaluated javascript file " + fileName + ". Generated the following JSON: " + result);
            return result;
        }
        finally
        {
            if (fileReader != null)
            {
                fileReader.close();
            }
        }
    }

    public String evaluateJavaScript(Reader fileReader)
    {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("JavaScript");
        try
        {
            engine.eval(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("json2.js")));
            engine.eval(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("test-utils.js")));
            engine.eval(fileReader);
            engine.eval("jsonString = JSON.stringify(" + TEST_CONFIG_VARIABLE_NAME + ")");
        }
        catch (ScriptException e)
        {
            throw new DistributedTestException("Exception while evaluating test config", e);
        }
        String result = (String) engine.get("jsonString");

        return result;
    }
}
