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

import java.io.File;
import java.util.TreeMap;

import org.apache.qpid.util.FileUtils;

import junit.framework.TestCase;

import com.google.gson.Gson;

public class JavaScriptConfigEvaluatorTest extends TestCase
{
    public void testEvaluateJavaScript() throws Exception
    {
        String jsFilePath = getClass().getResource("JavaScriptConfigEvaluatorTest-test-config.js").getPath();

        String rawConfig = new JavaScriptConfigEvaluator().evaluateJavaScript(jsFilePath);

        String config = formatForComparison(rawConfig);
        assertTrue(config.contains("\"_iterationNumber\":1"));

        File expectedJsonFile = new File(getClass().getResource("JavaScriptConfigEvaluatorTest-expected-json.json").getPath());
        String rawExpected = FileUtils.readFileAsString(expectedJsonFile);

        String expected = formatForComparison(rawExpected);

        assertEquals("Unexpected configuration", expected, config);
    }

    /**
     * Does an unmarshall-then-marshall on the supplied JSON string so that
     * we can compare the output when testing for equivalent JSON strings,
     * ignoring ordering of attributes.
     */
    private String formatForComparison(String jsonStringIn)
    {
        Gson gson = new Gson();

        @SuppressWarnings("rawtypes")
        TreeMap configObj = gson.fromJson(jsonStringIn, TreeMap.class);

        String jsonStringOut = gson.toJson(configObj);
        return jsonStringOut;
    }
}
