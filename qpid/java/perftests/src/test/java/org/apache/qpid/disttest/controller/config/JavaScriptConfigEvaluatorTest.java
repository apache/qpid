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

import static org.apache.commons.beanutils.PropertyUtils.getProperty;

import java.util.List;
import java.util.TreeMap;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

import com.google.gson.Gson;

public class JavaScriptConfigEvaluatorTest extends QpidTestCase
{
    public void testEvaluateJavaScript() throws Exception
    {
        String jsFilePath = TestFileUtils.createTempFileFromResource(this, "JavaScriptConfigEvaluatorTest-test-config.js").getAbsolutePath();

        String rawConfig = new JavaScriptConfigEvaluator().evaluateJavaScript(jsFilePath);

        Object configAsObject = getObject(rawConfig);

        // Tests are produced by the QPID.iterations js function
        assertEquals("Unexpected number of countries", 2, getPropertyAsList(configAsObject, "_countries").size());

        Object country0 = getProperty(configAsObject, "_countries.[0]");
        assertEquals("Unexpected country name", "Country", getProperty(country0, "_name"));
        assertEquals("Unexpected country iteration number", 0, getPropertyAsInt(country0, "_iterationNumber"));

        assertEquals("Unexpected number of regions", 2, getPropertyAsList(country0, "_regions").size());
        // Region names are produced by the QPID.times js function
        assertEquals("Unexpected region name", "repeatingRegion0", getProperty(country0, "_regions.[0]._name"));
        assertEquals("Unexpected region name", "repeatingRegion1", getProperty(country0, "_regions.[1]._name"));
        // Iterating attribute are produced by the QPID.iterations js function
        assertEquals("Unexpected iterating attribute", "0", getProperty(country0, "_regions.[0]._towns.[0]._iteratingAttribute"));

        Object country1 = getProperty(configAsObject, "_countries.[1]");
        assertEquals("Unexpected country iteration number", 1, getPropertyAsInt(country1, "_iterationNumber"));
        assertEquals("Unexpected iterating attribute", "1", getProperty(country1, "_regions.[0]._towns.[0]._iteratingAttribute"));
    }

    private int getPropertyAsInt(Object configAsObject, String property) throws Exception
    {
        Number propertyValue = (Number) getProperty(configAsObject, property);

        return propertyValue.intValue();
    }

    private List<?> getPropertyAsList(Object configAsObject, String property)
            throws Exception
    {
        return (List<?>)getProperty(configAsObject, property);
    }

    private Object getObject(String jsonStringIn)
    {
        Gson gson = new Gson();
        @SuppressWarnings("rawtypes")
        TreeMap object = gson.fromJson(jsonStringIn, TreeMap.class);
        return object;
    }
}
