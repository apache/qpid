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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

public class QpidRestTestCase extends QpidBrokerTestCase
{
    private static final Logger LOGGER = Logger.getLogger(QpidRestTestCase.class);

    public static final String[] EXPECTED_HOSTS = { "development", "test", "localhost" };
    public static final String[] EXPECTED_QUEUES = { "queue", "ping" };
    public static final String[] EXPECTED_EXCHANGES = { "amq.fanout", "amq.match", "amq.direct", "amq.topic",
            "qpid.management", "<<default>>" };

    private int _httpPort;
    private String _hostName;
    private List<HttpURLConnection> _httpConnections;

    public void setUp() throws Exception
    {
        _httpConnections = new ArrayList<HttpURLConnection>();
        _hostName = InetAddress.getLocalHost().getHostName();
        _httpPort = findFreePort();
        setConfigurationProperty("management.enabled", "true");
        setConfigurationProperty("management.http.enabled", "true");
        setConfigurationProperty("management.http.port", Integer.toString(_httpPort));
        setConfigurationProperty("management.jmx.enabled", "false");
        super.setUp();
    }

    public void teearDown() throws Exception
    {
        for (HttpURLConnection connection : _httpConnections)
        {
            try
            {
                connection.disconnect();
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        super.tearDown();
    }

    protected int getHttpPort()
    {
        return _httpPort;
    }

    protected String getHostName()
    {
        return _hostName;
    }

    protected String getManagementURL()
    {
        return "http://" + _hostName + ":" + _httpPort;
    }

    protected URL getManagementURL(String path) throws MalformedURLException
    {
        return new URL(getManagementURL() + path);
    }

    protected HttpURLConnection openManagementConection(String path) throws IOException
    {
        URL url = getManagementURL(path);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        return httpCon;
    }

    protected HttpURLConnection openManagementConection(String path, String method) throws IOException
    {
        HttpURLConnection httpCon = openManagementConection(path);
        httpCon.setRequestMethod(method);
        return httpCon;
    }

    protected List<Map<String, Object>> readJsonResponseAsList(HttpURLConnection connection) throws IOException,
            JsonParseException, JsonMappingException
    {
        byte[] data = readConnectionInputStream(connection);

        ObjectMapper mapper = new ObjectMapper();

        TypeReference<List<LinkedHashMap<String, Object>>> typeReference = new TypeReference<List<LinkedHashMap<String, Object>>>()
        {
        };
        List<Map<String, Object>> providedObject = mapper.readValue(new ByteArrayInputStream(data), typeReference);
        return providedObject;
    }

    protected Map<String, Object> readJsonResponseAsMap(HttpURLConnection connection) throws IOException,
            JsonParseException, JsonMappingException
    {
        byte[] data = readConnectionInputStream(connection);

        ObjectMapper mapper = new ObjectMapper();

        TypeReference<LinkedHashMap<String, Object>> typeReference = new TypeReference<LinkedHashMap<String, Object>>()
        {
        };
        Map<String, Object> providedObject = mapper.readValue(new ByteArrayInputStream(data), typeReference);
        return providedObject;
    }

    protected byte[] readConnectionInputStream(HttpURLConnection connection) throws IOException
    {
        InputStream is = connection.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = -1;
        while ((len = is.read(buffer)) != -1)
        {
            baos.write(buffer, 0, len);
        }
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("RESPONSE:" + new String(baos.toByteArray()));
        }
        return baos.toByteArray();
    }

    protected void writeJsonRequest(HttpURLConnection connection, Map<String, Object> data) throws JsonGenerationException,
            JsonMappingException, IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(connection.getOutputStream(), data);
    }

    protected Map<String, Object> find(String name, Object value, List<Map<String, Object>> data)
    {
        for (Map<String, Object> map : data)
        {
            Object mapValue = map.get(name);
            if (value.equals(mapValue))
            {
                return map;
            }
        }
        return null;
    }

    protected Map<String, Object> find(Map<String, Object> searchAttributes, List<Map<String, Object>> data)
    {
        for (Map<String, Object> map : data)
        {
            boolean equals = true;
            for (Map.Entry<String, Object> entry : searchAttributes.entrySet())
            {
                Object mapValue = map.get(entry.getKey());
                if (!entry.getValue().equals(mapValue))
                {
                    equals = false;
                    break;
                }
            }
            if (equals)
            {
                return map;
            }
        }
        return null;
    }

    protected Map<String, Object> getJsonAsSingletonList(String path) throws IOException
    {
        List<Map<String, Object>> response = getJsonAsList(path);

        assertNotNull("Response cannot be null", response);
        assertEquals("Unexpected response", 1, response.size());
        return response.get(0);
    }

    protected List<Map<String, Object>> getJsonAsList(String path) throws IOException, JsonParseException,
            JsonMappingException
    {
        HttpURLConnection connection = openManagementConection(path, "GET");
        connection.connect();
        List<Map<String, Object>> response = readJsonResponseAsList(connection);
        return response;
    }

    protected Map<String, Object> getJsonAsMap(String path) throws IOException
    {
        HttpURLConnection connection = openManagementConection(path, "GET");
        connection.connect();
        Map<String, Object> response = readJsonResponseAsMap(connection);
        return response;
    }
}
