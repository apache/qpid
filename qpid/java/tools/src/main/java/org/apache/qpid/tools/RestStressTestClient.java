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
package org.apache.qpid.tools;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.qpid.tools.util.ArgumentsParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

public class RestStressTestClient
{

    public static void main(String[] args) throws Exception
    {
        ArgumentsParser parser = new ArgumentsParser();
        Arguments arguments;
        try
        {
            arguments = parser.parse(args, Arguments.class);
            arguments.validate();
        }
        catch(IllegalArgumentException e)
        {
            System.out.println("Invalid argument:" + e.getMessage());
            parser.usage(Arguments.class, Arguments.REQUIRED);
            System.out.println("\nRun examples:" );
            System.out.println("  Using Basic authentication:" );
            System.out.println("  java -cp qpid-tools.jar:commons-codec.jar:jackson-core.jar:jackson-mapper.jar \\" );
            System.out.println("    -Djavax.net.ssl.trustStore=java_client_truststore.jks \\");
            System.out.println("    -Djavax.net.ssl.trustStorePassword=password \\");
            System.out.println("    org.apache.qpid.tools.RestStressTestClient \\");
            System.out.println("      repetitions=10 brokerUrl=https://localhost:8081 username=admin password=admin \\");
            System.out.println("      virtualHost=default virtualHostNode=default createQueue=true bindQueue=true \\");
            System.out.println("      deleteQueue=true uniqueQueues=true queueName=boo exchangeName=amq.fanout" );
            System.out.println("  Using CRAM-MD5 SASL authentication:" );
            System.out.println("  java -cp qpid-tools.jar:commons-codec.jar:jackson-core.jar:jackson-mapper.jar \\" );
            System.out.println("    org.apache.qpid.tools.RestStressTestClient saslMechanism=CRAM-MD5 \\");
            System.out.println("      repetitions=10 brokerUrl=http://localhost:8080 username=admin password=admin \\");
            System.out.println("      virtualHost=default virtualHostNode=default createQueue=true bindQueue=true \\");
            System.out.println("      deleteQueue=true uniqueQueues=true queueName=boo exchangeName=amq.fanout" );
            return;
        }

        RestStressTestClient client = new RestStressTestClient();
        client.run(arguments);
    }

    public void run(Arguments arguments) throws IOException
    {
        log(arguments.toString());
        for (int i = 0; i < arguments.getRepetitions(); i++)
        {
            runIteration(arguments, i);
        }
    }

    private void runIteration(Arguments arguments, int iteration) throws IOException
    {
        log("Iteration " + iteration);

        RestClient client = new RestClient(arguments.getBrokerUrl(), arguments.getUsername(), arguments.getPassword(), arguments.getSaslMechanism());
        client.authenticateIfSaslAuthenticationRequested();
        try
        {
            List<Map<String, Object>> brokerData = client.get("/api/latest/broker?depth=0");
            log("    Connected to broker " + brokerData.get(0).get("name"));
            createAndBindQueueIfRequired(arguments, client, iteration);
        }
        finally
        {
            if (arguments.isLogout())
            {
                client.logout();
            }
        }
    }

    private void log(String logMessage)
    {
        System.out.println(logMessage);
    }

    private void createAndBindQueueIfRequired(Arguments arguments, RestClient client, int iteration) throws IOException
    {
        if (arguments.isCreateQueue())
        {
            String virtualHostNode = arguments.getVirtualHostNode();
            String virtualHost = arguments.getVirtualHost();
            String queueName = arguments.getQueueName();

            if (queueName == null)
            {
                queueName = "temp-queue-" + System.nanoTime();
            }
            else if (arguments.isUniqueQueues())
            {
                queueName =  queueName + "-" + iteration;
            }

            createQueue(client, virtualHostNode, virtualHost, queueName);

            if (arguments.isBindQueue())
            {
                bindQueue(client, virtualHostNode, virtualHost, queueName, arguments.getExchangeName());
            }

            if (arguments.isDeleteQueue())
            {
                deleteQueue(client, virtualHostNode, virtualHost, queueName);
            }
        }
    }

    private void createQueue(RestClient client, String virtualHostNode, String virtualHost, String queueName) throws IOException
    {
        log("    Create queue " + queueName);

        String queueUrl = getQueueServiceUrl(virtualHostNode, virtualHost, queueName);
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("name", queueName);
        queueData.put("durable", true);

        int result = client.put(queueUrl, queueData);

        if (result != RestClient.RESPONSE_PUT_CREATE_OK)
        {
            throw new RuntimeException("Failure to create queue " + queueName);
        }
    }

    private String getQueueServiceUrl(String virtualHostNode, String virtualHost, String queueName)
    {
        return "/api/latest/queue/" + virtualHostNode + "/" + virtualHost + "/" + queueName;
    }

    private void deleteQueue(RestClient client, String virtualHostNode, String virtualHost, String queueName) throws IOException
    {
        log("    Delete queue " + queueName);
        int result = client.delete(getQueueServiceUrl(virtualHostNode, virtualHost, queueName));
        if (result != RestClient.RESPONSE_PUT_UPDATE_OK)
        {
            throw new RuntimeException("Failure to delete queue " + queueName);
        }
    }

    private void bindQueue(RestClient client, String virtualHostNode, String virtualHost, String queueName, String exchangeName)
            throws IOException
    {
        if (exchangeName == null)
        {
            exchangeName = "amq.direct";
        }

        log("        Bind queue " + queueName + " to " + exchangeName + " using binding key " + queueName);

        String bindingUrl = "/api/latest/binding/" + virtualHostNode + "/" + virtualHost + "/" + exchangeName + "/" + queueName + "/" + queueName;

        Map<String, Object> bindingData = new HashMap<>();
        bindingData.put("name", queueName);
        bindingData.put("queue", queueName);
        bindingData.put("exchange", exchangeName);

        int result = client.put(bindingUrl, bindingData);

        if (result != RestClient.RESPONSE_PUT_CREATE_OK)
        {
            throw new RuntimeException("Failure to bind queue " + queueName + " to " + exchangeName);
        }
    }

    public static class RestClient
    {
        private static final TypeReference<List<LinkedHashMap<String, Object>>> TYPE_LIST_OF_LINKED_HASH_MAPS = new TypeReference<List<LinkedHashMap<String, Object>>>()
        {
        };

        private static final TypeReference<LinkedHashMap<String, Object>> TYPE_HASH_MAP = new TypeReference<LinkedHashMap<String, Object>>()
        {
        };

        public static final int RESPONSE_PUT_CREATE_OK = 201;
        public static final int RESPONSE_PUT_UPDATE_OK = 200;
        public static final int RESPONSE_OK = 200;
        public static final int RESPONSE_AUTHENTICATION_REQUIRED = 401;

        private final ObjectMapper _mapper;
        private final String _brokerUrl;
        private final String _username;
        private final String _password;
        private final String _saslMechanism;
        private final String _authorizationHeader;

        private List<String> _cookies;

        public RestClient(String brokerUrl, String username, String password, String saslMechanism)
        {
            _mapper = new ObjectMapper();
            _brokerUrl = brokerUrl;
            _username = username;
            _password = password;
            _saslMechanism = saslMechanism;

            if (saslMechanism == null)
            {
                _authorizationHeader = "Basic " + new String(new Base64().encode((_username + ":" + _password).getBytes()));
            }
            else
            {
                _authorizationHeader = null;
            }
        }

        public List<Map<String, Object>> get(String restServiceUrl) throws IOException
        {
            HttpURLConnection connection = createConnection("GET", restServiceUrl, _cookies);
            try
            {
                connection.connect();
                byte[] data = readConnectionInputStream(connection);
                checkResponseCode(connection);
                return _mapper.readValue(new ByteArrayInputStream(data), TYPE_LIST_OF_LINKED_HASH_MAPS);
            }
            finally
            {
                connection.disconnect();
            }
        }

        public int put(String restServiceUrl, Map<String, Object> attributes) throws IOException
        {
            HttpURLConnection connection = createConnection("PUT", restServiceUrl, _cookies);
            try
            {
                connection.connect();
                if (attributes != null)
                {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.writeValue(connection.getOutputStream(), attributes);
                }
                checkResponseCode(connection);
                return connection.getResponseCode();
            }
            finally
            {
                connection.disconnect();
            }
        }

        public int delete(String restServiceUrl) throws IOException
        {
            HttpURLConnection connection = createConnection("DELETE", restServiceUrl, _cookies);
            try
            {
                checkResponseCode(connection);
                return connection.getResponseCode();
            }
            finally
            {
                connection.disconnect();
            }
        }

        public int post(String restServiceUrl, Map<String, String> postData) throws  IOException
        {
            HttpURLConnection connection = createConnectionAndPostData(restServiceUrl, postData, _cookies);
            try
            {
                checkResponseCode(connection);
                return connection.getResponseCode();
            }
            finally
            {
                connection.disconnect();
            }
        }

        private HttpURLConnection createConnectionAndPostData(String restServiceUrl, Map<String, String> postData, List<String> cookies) throws IOException
        {
            String postParameters = getPostDataString(postData);
            HttpURLConnection connection = createConnection("POST", restServiceUrl, cookies);
            try
            {
                OutputStream os = connection.getOutputStream();
                os.write(postParameters.getBytes());
                os.flush();
            }
            catch (IOException e)
            {
                connection.disconnect();
                throw e;
            }
            return connection;
        }

        private void checkResponseCode(HttpURLConnection connection) throws IOException
        {
            if (connection.getResponseCode() == RESPONSE_AUTHENTICATION_REQUIRED)
            {
                _cookies = null;
                throw new IllegalArgumentException("Authentication is required");
            }
        }

        private String getPostDataString(Map<String, String> postData)
        {
            StringBuilder sb = new StringBuilder();
            if (postData != null)
            {
                Iterator<String> iterator = postData.keySet().iterator();
                while (iterator.hasNext())
                {
                    String key = iterator.next();
                    sb.append(key + "=" + postData.get(key));
                    if (iterator.hasNext())
                    {
                        sb.append("&");
                    }
                }
            }
            return sb.toString();
        }

        private HttpURLConnection createConnection(String method, String restServiceUrl, List<String> cookies) throws IOException
        {
            HttpURLConnection httpConnection = (HttpURLConnection) new URL(_brokerUrl + restServiceUrl).openConnection();
            if (cookies != null)
            {
                for (String cookie : cookies)
                {
                    httpConnection.addRequestProperty("Cookie", cookie.split(";", 2)[0]);
                }
            }
            if (_saslMechanism == null)
            {
                httpConnection.setRequestProperty("Authorization", _authorizationHeader);
            }

            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod(method);
            return httpConnection;
        }

        public void authenticateIfSaslAuthenticationRequested() throws IOException
        {
            if (_saslMechanism == null)
            {
                // basic authentication will be used with each request
            }
            else if ("CRAM-MD5".equals(_saslMechanism))
            {
                _cookies = performCramMD5Authentication();
            }
            else
            {
                throw new IllegalArgumentException("Unsupported SASL mechanism :" + _saslMechanism);
            }
        }


        public void logout() throws IOException
        {
            if (_cookies != null)
            {
                HttpURLConnection connection = createConnection("GET", "/service/logout", _cookies);
                try
                {
                    connection.connect();
                    _cookies = null;
                }
                finally
                {
                    connection.disconnect();
                }
            }

            //TODO: we need to track sessions for basic auth in order to logout those
        }

        private List<String> performCramMD5Authentication() throws IOException
        {
            // request the challenge for CRAM-MD5
            HttpURLConnection connection = createConnectionAndPostData("/service/sasl", Collections.singletonMap("mechanism", "CRAM-MD5"), null);
            try
            {
                List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

                // get response
                byte[] data = readConnectionInputStream(connection);
                Map<String, Object> response = _mapper.readValue(new ByteArrayInputStream(data), TYPE_HASH_MAP);
                String challenge = (String) response.get("challenge");

                // generate the authentication response for the received challenge
                String responseData = generateResponseForChallengeAndCredentials(challenge, _username, _password);

                Map<String, String> saslResponse = new HashMap<>();
                saslResponse.put("id", (String)response.get("id"));
                saslResponse.put("response", responseData);

                HttpURLConnection authenticateConnection = createConnectionAndPostData("/service/sasl", saslResponse, cookies);
                try
                {
                    int code = authenticateConnection.getResponseCode();
                    if (code != RESPONSE_OK)
                    {
                        throw new RuntimeException("Authentication failed");
                    }
                    else
                    {
                        return cookies;
                    }
                }
                finally
                {
                    authenticateConnection.disconnect();
                }
            }
            finally
            {
                connection.disconnect();
            }
        }

        private String generateResponseForChallengeAndCredentials(String challenge, String username, String password)
        {
            try
            {
                byte[] challengeBytes = Base64.decodeBase64(challenge);

                String macAlgorithm = "HmacMD5";
                Mac mac = Mac.getInstance(macAlgorithm);
                mac.init(new SecretKeySpec(password.getBytes("UTF-8"), macAlgorithm));
                final byte[] messageAuthenticationCode = mac.doFinal(challengeBytes);
                String responseAsString = username + " " + toHex(messageAuthenticationCode);
                byte[] responseBytes = responseAsString.getBytes();
                return Base64.encodeBase64String(responseBytes);
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("Unexpected exception", e);
            }
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

        private byte[] readConnectionInputStream(HttpURLConnection connection) throws IOException
        {
            if (connection.getResponseCode() == RESPONSE_AUTHENTICATION_REQUIRED)
            {
                _cookies = null;
            }
            InputStream is = connection.getInputStream();
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1)
                {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        }

    }

    public static class Arguments
    {
        private static final Set<String> REQUIRED = new HashSet<>(Arrays.asList("brokerUrl", "username", "password"));

        private String brokerUrl = null;
        private String username = null;
        private String password = null;
        private String saslMechanism = null;

        private String virtualHostNode = null;
        private String virtualHost = null;
        private String queueName = null;
        private String exchangeName = null;

        private int repetitions = 1;

        private boolean createQueue = false;
        private boolean deleteQueue = false;
        private boolean uniqueQueues = false;
        private boolean bindQueue = false;

        private boolean logout = true;

        public Arguments()
        {
        }

        public void validate()
        {
            if (brokerUrl == null || brokerUrl.equals(""))
            {
                throw new IllegalArgumentException("Mandatory argument 'brokerUrl' is not specified");
            }

            if (username == null || username.equals(""))
            {
                throw new IllegalArgumentException("Mandatory argument 'username' is not specified");
            }

            if (password == null || password.equals(""))
            {
                throw new IllegalArgumentException("Mandatory argument 'password' is not specified");
            }

            if (createQueue)
            {
                if (virtualHostNode == null || virtualHostNode.equals(""))
                {
                    throw new IllegalArgumentException("Virtual host node name needs to be specified for queue creation");
                }

                if (virtualHost == null || virtualHost.equals(""))
                {
                    throw new IllegalArgumentException("Virtual host name needs to be specified for queue creation");
                }
            }
        }

        public String getUsername()
        {
            return username;
        }

        public String getPassword()
        {
            return password;
        }

        public String getVirtualHost()
        {
            return virtualHost;
        }

        public boolean isCreateQueue()
        {
            return createQueue;
        }

        public boolean isDeleteQueue()
        {
            return deleteQueue;
        }

        public boolean isUniqueQueues()
        {
            return uniqueQueues;
        }

        public String getQueueName()
        {
            return queueName;
        }

        public boolean isBindQueue()
        {
            return bindQueue;
        }

        public String getExchangeName()
        {
            return exchangeName;
        }

        public String getVirtualHostNode()
        {
            return virtualHostNode;
        }


        public int getRepetitions()
        {
            return repetitions;
        }

        public String getBrokerUrl()
        {
            return brokerUrl;
        }

        public String getSaslMechanism()
        {
            return saslMechanism;
        }

        public boolean isLogout()
        {
            return logout;
        }

        @Override
        public String toString()
        {
            return "Arguments{" +
                    "brokerUrl='" + brokerUrl + '\'' +
                    ", username='" + username + '\'' +
                    ", password='" + password + '\'' +
                    ", saslMechanism='" + saslMechanism + '\'' +
                    ", virtualHostNode='" + virtualHostNode + '\'' +
                    ", virtualHost='" + virtualHost + '\'' +
                    ", queueName='" + queueName + '\'' +
                    ", exchangeName='" + exchangeName + '\'' +
                    ", repetitions=" + repetitions +
                    ", createQueue=" + createQueue +
                    ", deleteQueue=" + deleteQueue +
                    ", uniqueQueues=" + uniqueQueues +
                    ", bindQueue=" + bindQueue +
                    ", logout=" + logout +
                    '}';
        }
    }

}
