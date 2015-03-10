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
 */
package org.apache.qpid.systest.rest;

import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE_PASSWORD;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE_PASSWORD;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class RestTestHelper
{

    private static final TypeReference<List<LinkedHashMap<String, Object>>> TYPE_LIST_OF_LINKED_HASH_MAPS = new TypeReference<List<LinkedHashMap<String, Object>>>()
    {
    };
    public static final String API_BASE = "/api/latest/";

    private static final Logger LOGGER = Logger.getLogger(RestTestHelper.class);
    private static final String CERT_ALIAS_APP1 = "app1";

    private int _httpPort;

    private boolean _useSsl;


    private String _username;

    private String _password;

    private File _passwdFile;
    private boolean _useSslAuth;
    static final String[] EXPECTED_QUEUES = { "queue", "ping" };
    private final int _connectTimeout = Integer.getInteger("qpid.resttest_connection_timeout", 30000);

    public RestTestHelper(int httpPort)
    {
        _httpPort = httpPort;
    }

    public int getHttpPort()
    {
        return _httpPort;
    }

    private String getHostName()
    {
        return "localhost";
    }

    private String getProtocol()
    {
        return _useSsl ? "https" : "http";
    }

    public String getManagementURL()
    {
        return getProtocol() + "://" + getHostName() + ":" + getHttpPort();
    }

    public URL getManagementURL(String path) throws MalformedURLException
    {
        return new URL(getManagementURL() + path);
    }

    public HttpURLConnection openManagementConnection(String path, String method) throws IOException
    {
        if (!path.startsWith("/"))
        {
            path = API_BASE + path;
        }
        URL url = getManagementURL(path);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setConnectTimeout(_connectTimeout);

        if(_useSslAuth)
        {
            try
            {
                // We have to use a SSLSocketFactory from a new SSLContext so that we don't re-use
                // the JVM's defaults that may have been initialised in previous tests.

                SSLContext sslContext = SSLContextFactory.buildClientContext(
                        TRUSTSTORE, TRUSTSTORE_PASSWORD,
                        KeyStore.getDefaultType(),
                        TrustManagerFactory.getDefaultAlgorithm(),
                        KEYSTORE, KEYSTORE_PASSWORD, KeyStore.getDefaultType(), KeyManagerFactory.getDefaultAlgorithm(), CERT_ALIAS_APP1);

                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                ((HttpsURLConnection) httpCon).setSSLSocketFactory(sslSocketFactory);
            }
            catch (GeneralSecurityException e)
            {
                throw new RuntimeException(e);
            }
        }
        else if(_useSsl)
        {
            try
            {
                // We have to use a SSLSocketFactory from a new SSLContext so that we don't re-use
                // the JVM's defaults that may have been initialised in previous tests.

                SSLContext sslContext = SSLContextFactory.buildClientContext(
                        TRUSTSTORE, TRUSTSTORE_PASSWORD,
                        KeyStore.getDefaultType(),
                        TrustManagerFactory.getDefaultAlgorithm(),
                        null, null, null, null, null);

                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                ((HttpsURLConnection) httpCon).setSSLSocketFactory(sslSocketFactory);
            }
            catch (GeneralSecurityException e)
            {
                throw new RuntimeException(e);
            }
        }

        if(_username != null)
        {
            String encoded = new String(new Base64().encode((_username + ":" + _password).getBytes()));
            httpCon.setRequestProperty("Authorization", "Basic " + encoded);
        }

        httpCon.setDoOutput(true);
        httpCon.setRequestMethod(method);
        return httpCon;
    }

    public List<Map<String, Object>> readJsonResponseAsList(HttpURLConnection connection) throws IOException,
            JsonParseException, JsonMappingException
    {
        byte[] data = readConnectionInputStream(connection);
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> providedObject = mapper.readValue(new ByteArrayInputStream(data), TYPE_LIST_OF_LINKED_HASH_MAPS);
        return providedObject;
    }

    public Map<String, Object> readJsonResponseAsMap(HttpURLConnection connection) throws IOException,
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

    private byte[] readConnectionInputStream(HttpURLConnection connection) throws IOException
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

    private void writeJsonRequest(HttpURLConnection connection, Map<String, Object> data) throws JsonGenerationException,
            JsonMappingException, IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(connection.getOutputStream(), data);
    }

    public Map<String, Object> find(String name, Object value, List<Map<String, Object>> data)
    {
        if (data == null)
        {
            return null;
        }

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

    public Map<String, Object> find(Map<String, Object> searchAttributes, List<Map<String, Object>> data)
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

    public Map<String, Object> getJsonAsSingletonList(String path) throws IOException
    {
        List<Map<String, Object>> response = getJsonAsList(path);

        Assert.assertNotNull("Response cannot be null", response);
        Assert.assertEquals("Unexpected response from " + path, 1, response.size());
        return response.get(0);
    }

    public List<Map<String, Object>> getJsonAsList(String path) throws IOException, JsonParseException,
            JsonMappingException
    {
        HttpURLConnection connection = openManagementConnection(path, "GET");
        connection.connect();
        List<Map<String, Object>> response = readJsonResponseAsList(connection);
        return response;
    }

    public Map<String, Object> getJsonAsMap(String path) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(path, "GET");
        connection.connect();
        Map<String, Object> response = readJsonResponseAsMap(connection);
        return response;
    }

    public void createNewGroupMember(String groupProviderName, String groupName, String memberName, int responseCode) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(
                "groupmember/" + encodeAsUTF(groupProviderName) + "/"+ encodeAsUTF(groupName) + "/" +  encodeAsUTF(memberName),
                "POST");

        Map<String, Object> groupMemberData = new HashMap<String, Object>();
        // TODO add type
        writeJsonRequest(connection, groupMemberData);

        Assert.assertEquals("Unexpected response code", responseCode, connection.getResponseCode());

        connection.disconnect();
    }

    public void createNewGroupMember(String groupProviderName, String groupName, String memberName) throws IOException
    {
        createNewGroupMember(groupProviderName, groupName, memberName, HttpServletResponse.SC_CREATED);
    }

    public void removeMemberFromGroup(String groupProviderName, String groupName, String memberName, int responseCode) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(
                "groupmember/" + encodeAsUTF(groupProviderName) + "/"+ encodeAsUTF(groupName) + "/" +  encodeAsUTF(memberName),
                "DELETE");

        Assert.assertEquals("Unexpected response code", responseCode, connection.getResponseCode());

        connection.disconnect();
    }

    public void removeMemberFromGroup(String groupProviderName, String groupName, String memberName) throws IOException
    {
        removeMemberFromGroup(groupProviderName, groupName, memberName, HttpServletResponse.SC_OK);
    }

    public void assertNumberOfGroupMembers(Map<String, Object> data, int expectedNumberOfGroupMembers)
    {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("groupmembers");
        if (groups == null)
        {
            groups = Collections.emptyList();
        }

        Assert.assertEquals("Unexpected number of group members", expectedNumberOfGroupMembers, groups.size());
    }

    public void createGroup(String groupName, String groupProviderName) throws IOException
    {
        createGroup(groupName, groupProviderName, HttpServletResponse.SC_CREATED);
    }

    public void createGroup(String groupName, String groupProviderName, int responseCode) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(
                "group/" + encodeAsUTF(groupProviderName) + "/"+ encodeAsUTF(groupName),
                "POST");

        Map<String, Object> groupData = new HashMap<String, Object>();
        writeJsonRequest(connection, groupData);

        Assert.assertEquals("Unexpected response code", responseCode, connection.getResponseCode());

        connection.disconnect();
    }

    public void createOrUpdateUser(String username, String password) throws IOException
    {
        createOrUpdateUser(username, password, HttpServletResponse.SC_CREATED);
    }

    public void createOrUpdateUser(String username, String password, int responseCode) throws IOException
    {
        HttpURLConnection connection = openManagementConnection("user/"
                + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + username, "PUT");

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("password", password);
        writeJsonRequest(connection, data);

        Assert.assertEquals("Unexpected response code", responseCode, connection.getResponseCode());

        connection.disconnect();
    }

    public void removeGroup(String groupName, String groupProviderName, int responseCode) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(
                "group/" + encodeAsUTF(groupProviderName) + "/"+ encodeAsUTF(groupName),
                "DELETE");

        Assert.assertEquals("Unexpected response code", responseCode, connection.getResponseCode());
        connection.disconnect();
    }

    public void removeGroup(String groupName, String groupProviderName) throws IOException
    {
        removeGroup(groupName, groupProviderName, HttpServletResponse.SC_OK);
    }

    public void removeUserById(String id) throws IOException
    {
        HttpURLConnection connection = openManagementConnection("user/"
                + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "?id=" + id, "DELETE");
        Assert.assertEquals("Unexpected response code", HttpServletResponse.SC_OK, connection.getResponseCode());
        connection.disconnect();
    }

    public void removeUser(String username, int responseCode) throws IOException
    {
        HttpURLConnection connection = openManagementConnection("user/"
                + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + username, "DELETE");
        Assert.assertEquals("Unexpected response code", responseCode, connection.getResponseCode());
        connection.disconnect();
    }

    public void removeUser(String username) throws IOException
    {
        removeUser(username, HttpServletResponse.SC_OK);
    }

    public void assertNumberOfGroups(Map<String, Object> data, int expectedNumberOfGroups)
    {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("groups");
        if (groups == null)
        {
            groups = Collections.emptyList();
        }
        Assert.assertEquals("Unexpected number of groups", expectedNumberOfGroups, groups.size());
    }

    public void setUseSsl(boolean useSsl)
    {
        _useSsl = useSsl;
    }

    public void setUsernameAndPassword(String username, String password)
    {
        _username = username;
        _password = password;
    }

    public void setManagementModeCredentials()
    {
        setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, QpidBrokerTestCase.MANAGEMENT_MODE_PASSWORD);
    }

    /**
     * Create password file that follows the convention username=password, which is deleted by {@link #tearDown()}
     */
    public void configureTemporaryPasswordFile(QpidBrokerTestCase testCase, String... users) throws IOException
    {
        _passwdFile = createTemporaryPasswdFile(users);

        testCase.getBrokerConfiguration().setObjectAttribute(AuthenticationProvider.class, TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER,
                "path", _passwdFile.getAbsolutePath());
    }

    public void tearDown()
    {
        if (_passwdFile != null)
        {
            if (_passwdFile.exists())
            {
                _passwdFile.delete();
            }
        }
    }

    File createTemporaryPasswdFile(String[] users) throws IOException
    {
        BufferedWriter writer = null;
        try
        {
            File testFile = File.createTempFile(this.getClass().getName(),"tmp");
            testFile.deleteOnExit();

            writer = new BufferedWriter(new FileWriter(testFile));
            for (int i = 0; i < users.length; i++)
            {
                String username = users[i];
                writer.write(username + ":" + username);
                writer.newLine();
            }

            return testFile;

        }
        finally
        {
            if (writer != null)
            {
                writer.close();
            }
        }
    }

    public int submitRequest(String url, String method, Map<String, Object> attributes) throws IOException,
            JsonGenerationException, JsonMappingException
    {
        HttpURLConnection connection = openManagementConnection(url, method);
        if (attributes != null)
        {
            writeJsonRequest(connection, attributes);
        }
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        return responseCode;
    }

    public int submitRequest(String url, String method) throws IOException
    {
        return submitRequest(url, method, (byte[])null);
    }

    public void submitRequest(String url, String method, Map<String, Object> attributes, int expectedResponseCode) throws IOException
    {
        int responseCode = submitRequest(url, method, attributes);
        Assert.assertEquals("Unexpected response code from " + method + " " + url , expectedResponseCode, responseCode);
    }

    public void submitRequest(String url, String method, int expectedResponseCode) throws IOException
    {
        submitRequest(url, method, null, expectedResponseCode);
    }

    public int submitRequest(String url, String method, byte[] parameters) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(url, method);
        if (parameters != null)
        {
            OutputStream os = connection.getOutputStream();
            os.write(parameters);
            os.flush();
        }
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        return responseCode;
    }

    public byte[] getBytes(String path) throws IOException
    {
        HttpURLConnection connection = openManagementConnection(path, "GET");
        connection.connect();
        return readConnectionInputStream(connection);
    }

    public void setUseSslAuth(final boolean useSslAuth)
    {
        _useSslAuth = useSslAuth;
        _useSsl = true;
    }

    public void createTestQueues() throws IOException, JsonGenerationException, JsonMappingException
    {
        for (int i = 0; i < EXPECTED_QUEUES.length; i++)
        {
            String queueName = EXPECTED_QUEUES[i];
            Map<String, Object> queueData = new HashMap<String, Object>();
            queueData.put(Queue.NAME, queueName);
            queueData.put(Queue.DURABLE, Boolean.FALSE);
            int responseCode = submitRequest("queue/test/test/" + queueName, "PUT", queueData);
            Assert.assertEquals("Unexpected response code creating queue" + queueName, 201, responseCode);

            Map<String, Object> bindingData = new HashMap<String, Object>();
            bindingData.put(Binding.NAME, queueName);
            bindingData.put(Binding.QUEUE, queueName);
            bindingData.put(Binding.EXCHANGE, "amq.direct");
            responseCode = submitRequest("binding/test/test/amq.direct/" + queueName + "/" + queueName, "PUT", queueData);
            Assert.assertEquals("Unexpected response code binding queue " + queueName, 201, responseCode);
        }
    }

    public String encode(String value, String encoding) throws UnsupportedEncodingException
    {
        return URLEncoder.encode(value, encoding).replace("+", "%20");
    }

    public String encodeAsUTF(String value)
    {
        try
        {
            return encode(value, "UTF8");
        }
        catch(UnsupportedEncodingException e)
        {
            throw new RuntimeException("Unsupported encoding UTF8", e);
        }
    }
}
