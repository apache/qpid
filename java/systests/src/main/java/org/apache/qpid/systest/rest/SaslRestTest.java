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
package org.apache.qpid.systest.rest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.AbstractPrincipalDatabaseAuthManagerFactory;
import org.apache.qpid.server.security.auth.manager.Base64MD5PasswordFileAuthenticationManagerFactory;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.tools.security.Passwd;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;

public class SaslRestTest extends QpidRestTestCase
{
    @Override
    public void startBroker()
    {
        // prevent broker from starting in setUp
    }

    public void startBrokerNow() throws Exception
    {
        super.startBroker();
    }

    public void testGetMechanismsWithBrokerPlainPasswordPrincipalDatabase() throws Exception
    {
        startBrokerNow();

        Map<String, Object> saslData = getRestTestHelper().getJsonAsMap("/rest/sasl");
        assertNotNull("mechanisms attribute is not found", saslData.get("mechanisms"));

        @SuppressWarnings("unchecked")
        List<String> mechanisms = (List<String>) saslData.get("mechanisms");
        String[] expectedMechanisms = { "AMQPLAIN", "PLAIN", "CRAM-MD5" };
        for (String mechanism : expectedMechanisms)
        {
            assertTrue("Mechanism " + mechanism + " is not found", mechanisms.contains(mechanism));
        }
        assertNull("Unexpected user was returned", saslData.get("user"));
    }

    public void testGetMechanismsWithBrokerBase64MD5FilePrincipalDatabase() throws Exception
    {
        configureBase64MD5FilePrincipalDatabase();
        startBrokerNow();

        Map<String, Object> saslData = getRestTestHelper().getJsonAsMap("/rest/sasl");
        assertNotNull("mechanisms attribute is not found", saslData.get("mechanisms"));

        @SuppressWarnings("unchecked")
        List<String> mechanisms = (List<String>) saslData.get("mechanisms");
        String[] expectedMechanisms = { "CRAM-MD5-HEX", "CRAM-MD5-HASHED" };
        for (String mechanism : expectedMechanisms)
        {
            assertTrue("Mechanism " + mechanism + " is not found", mechanisms.contains(mechanism));
        }
        assertNull("Unexpected user was returned", saslData.get("user"));
    }

    public void testPlainSaslAuthenticationForValidCredentials() throws Exception
    {
        startBrokerNow();

        byte[] responseBytes = generatePlainClientResponse("admin", "admin");
        String responseData = Base64.encodeBase64String(responseBytes);
        String parameters= "mechanism=PLAIN&response=" + responseData;

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/sasl", "POST");
        OutputStream os = connection.getOutputStream();
        os.write(parameters.getBytes());
        os.flush();

         int code = connection.getResponseCode();
        assertEquals("Unexpected response code", 200, code);

        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertEquals("Unexpected user", "admin", response2.get("user"));
    }

    public void testPlainSaslAuthenticationForIncorrectPassword() throws Exception
    {
        startBrokerNow();

        byte[] responseBytes = generatePlainClientResponse("admin", "incorrect");
        String responseData = Base64.encodeBase64String(responseBytes);
        String parameters= "mechanism=PLAIN&response=" + responseData;

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/sasl", "POST");
        OutputStream os = connection.getOutputStream();
        os.write(parameters.getBytes());
        os.flush();

        int code = connection.getResponseCode();
        assertEquals("Unexpected response code", 403, code);

        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertNull("Unexpected user", response2.get("user"));
    }

    public void testPlainSaslAuthenticationForNonExistingUser() throws Exception
    {
        startBrokerNow();

        byte[] responseBytes = generatePlainClientResponse("nonexisting", "admin");
        String responseData = Base64.encodeBase64String(responseBytes);
        String parameters= "mechanism=PLAIN&response=" + responseData;

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/sasl", "POST");
        OutputStream os = connection.getOutputStream();
        os.write(parameters.getBytes());
        os.flush();

        int code = connection.getResponseCode();
        assertEquals("Unexpected response code", 403, code);

        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertNull("Unexpected user", response2.get("user"));
    }

    public void testCramMD5SaslAuthenticationForValidCredentials() throws Exception
    {
        startBrokerNow();

        // request the challenge for CRAM-MD5
        HttpURLConnection connection = requestSasServerChallenge("CRAM-MD5");
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // authenticate user with correct credentials
        int code = authenticateUser(connection, "admin", "admin", "CRAM-MD5");
        assertEquals("Unexpected response code", 200, code);

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertEquals("Unexpected user", "admin", response2.get("user"));
    }

    public void testCramMD5SaslAuthenticationForIncorrectPassword() throws Exception
    {
        startBrokerNow();

        // request the challenge for CRAM-MD5
        HttpURLConnection connection = requestSasServerChallenge("CRAM-MD5");
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // authenticate user with correct credentials
        int code = authenticateUser(connection, "admin", "incorrect", "CRAM-MD5");
        assertEquals("Unexpected response code", 403, code);

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertNull("Unexpected user", response2.get("user"));
    }

    public void testCramMD5SaslAuthenticationForNonExistingUser() throws Exception
    {
        startBrokerNow();

        // request the challenge for CRAM-MD5
        HttpURLConnection connection = requestSasServerChallenge("CRAM-MD5");
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // authenticate user with correct credentials
        int code = authenticateUser(connection, "nonexisting", "admin", "CRAM-MD5");
        assertEquals("Unexpected response code", 403, code);

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertNull("Unexpected user",  response2.get("user"));
    }

    public void testCramMD5HexSaslAuthenticationForValidCredentials() throws Exception
    {
        configureBase64MD5FilePrincipalDatabase();
        startBrokerNow();

        // request the challenge for CRAM-MD5-HEX
        HttpURLConnection connection = requestSasServerChallenge("CRAM-MD5-HEX");
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // authenticate user with correct credentials
        int code = authenticateUser(connection, "admin", "admin", "CRAM-MD5-HEX");
        assertEquals("Unexpected response code", 200, code);

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertEquals("Unexpected user", "admin", response2.get("user"));
    }

    public void testCramMD5HexSaslAuthenticationForIncorrectPassword() throws Exception
    {
        configureBase64MD5FilePrincipalDatabase();
        startBrokerNow();

        HttpURLConnection connection = requestSasServerChallenge("CRAM-MD5-HEX");
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // try to authenticate user with incorrect passowrd
        int code = authenticateUser(connection, "admin", "incorrect", "CRAM-MD5-HEX");
        assertEquals("Unexpected response code", 403, code);

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertNull("Unexpected user", response2.get("user"));
    }

    public void testCramMD5HexSaslAuthenticationForNonExistingUser() throws Exception
    {
        configureBase64MD5FilePrincipalDatabase();
        startBrokerNow();

        HttpURLConnection connection = requestSasServerChallenge("CRAM-MD5-HEX");
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

        // try to authenticate non-existing user
        int code = authenticateUser(connection, "nonexisting", "admin", "CRAM-MD5-HEX");
        assertEquals("Unexpected response code", 403, code);

        // request authenticated user details
        connection = getRestTestHelper().openManagementConnection("/rest/sasl", "GET");
        applyCookiesToConnection(cookies, connection);
        Map<String, Object> response2 = getRestTestHelper().readJsonResponseAsMap(connection);
        assertNull("Unexpected user", response2.get("user"));
    }

    private HttpURLConnection requestSasServerChallenge(String mechanism) throws IOException
    {
        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/sasl", "POST");
        OutputStream os = connection.getOutputStream();
        os.write(("mechanism=" + mechanism).getBytes());
        os.flush();
        return connection;
    }

    private int authenticateUser(HttpURLConnection requestChallengeConnection, String userName, String userPassword, String mechanism)
            throws IOException, JsonParseException, JsonMappingException, Exception
    {
        // get the response
        Map<String, Object> response = getRestTestHelper().readJsonResponseAsMap(requestChallengeConnection);
        String challenge = (String) response.get("challenge");
        assertNotNull("Challenge is not found", challenge);

        // preserve cookies to have the same server session
        List<String> cookies = requestChallengeConnection.getHeaderFields().get("Set-Cookie");

        // generate the authentication response for the challenge received
        byte[] challengeBytes = Base64.decodeBase64(challenge);
        byte[] responseBytes = generateClientResponse(mechanism, userName, userPassword, challengeBytes);
        String responseData = Base64.encodeBase64String(responseBytes);
        String requestParameters = ("id=" + response.get("id") + "&response=" + responseData);

        // re-open connection
        HttpURLConnection authenticateConnection = getRestTestHelper().openManagementConnection("/rest/sasl", "POST");

        // set cookies to use the same server session
        applyCookiesToConnection(cookies, authenticateConnection);
        OutputStream os = authenticateConnection.getOutputStream();
        os.write(requestParameters.getBytes());
        os.flush();
        return authenticateConnection.getResponseCode();
    }

    private byte[] generateClientResponse(String mechanism, String userName, String userPassword, byte[] challengeBytes) throws Exception
    {
        byte[] responseBytes =  null;
        if ("CRAM-MD5-HEX".equalsIgnoreCase(mechanism))
        {
            responseBytes = generateCramMD5HexClientResponse(userName, userPassword, challengeBytes);
        }
        else if ("CRAM-MD5".equalsIgnoreCase(mechanism))
        {
            responseBytes = generateCramMD5ClientResponse(userName, userPassword, challengeBytes);
        }
        else
        {
            throw new RuntimeException("Not implemented test mechanism " + mechanism);
        }
        return responseBytes;
    }

    private void applyCookiesToConnection(List<String> cookies, HttpURLConnection connection)
    {
        for (String cookie : cookies)
        {
            connection.addRequestProperty("Cookie", cookie.split(";", 2)[0]);
        }
    }

    private static byte SEPARATOR = 0;

    private byte[] generatePlainClientResponse(String userName, String userPassword) throws Exception
    {
        byte[] password = userPassword.getBytes("UTF8");
        byte user[] = userName.getBytes("UTF8");
        byte response[] = new byte[password.length + user.length + 2 ];
        int size = 0;
        response[size++] = SEPARATOR;
        System.arraycopy(user, 0, response, size, user.length);
        size += user.length;
        response[size++] = SEPARATOR;
        System.arraycopy(password, 0, response, size, password.length);
        return response;
    }

    private byte[] generateCramMD5HexClientResponse(String userName, String userPassword, byte[] challengeBytes) throws Exception
    {
        String macAlgorithm = "HmacMD5";
        byte[] digestedPasswordBytes = MessageDigest.getInstance("MD5").digest(userPassword.getBytes("UTF-8"));
        byte[] hexEncodedDigestedPasswordBytes = toHex(digestedPasswordBytes).getBytes("UTF-8");
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(hexEncodedDigestedPasswordBytes, macAlgorithm));
        final byte[] messageAuthenticationCode = mac.doFinal(challengeBytes);
        String responseAsString = userName + " " + toHex(messageAuthenticationCode);
        return responseAsString.getBytes();
    }

    private byte[] generateCramMD5ClientResponse(String userName, String userPassword, byte[] challengeBytes) throws Exception
    {
        String macAlgorithm = "HmacMD5";
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(userPassword.getBytes("UTF-8"), macAlgorithm));
        final byte[] messageAuthenticationCode = mac.doFinal(challengeBytes);
        String responseAsString = userName + " " + toHex(messageAuthenticationCode);
        return responseAsString.getBytes();
    }

    private String toHex(byte[] data)
    {
        StringBuffer hash = new StringBuffer();
        for (int i = 0; i < data.length; i++)
        {
            String hex = Integer.toHexString(0xFF & data[i]);
            if (hex.length() == 1)
            {
                hash.append('0');
            }
            hash.append(hex);
        }
        return hash.toString();
    }

    private void configureBase64MD5FilePrincipalDatabase() throws IOException, ConfigurationException
    {
        // generate user password entry
        String passwordFileEntry;
        try
        {
            passwordFileEntry = new Passwd().getOutput("admin", "admin");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ConfigurationException(e);
        }

        // store the entry in the file
        File passwordFile = File.createTempFile("passwd", "pwd");
        passwordFile.deleteOnExit();

        FileWriter writer = null;
        try
        {
            writer = new FileWriter(passwordFile);
            writer.write(passwordFileEntry);
        }
        finally
        {
            writer.close();
        }

        // configure broker to use Base64MD5PasswordFilePrincipalDatabase
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_PATH, passwordFile.getAbsolutePath());
        newAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, Base64MD5PasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        getBrokerConfiguration().setObjectAttributes(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, newAttributes);
    }
}
