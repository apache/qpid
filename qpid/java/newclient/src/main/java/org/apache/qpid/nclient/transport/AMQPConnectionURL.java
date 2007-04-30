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
package org.apache.qpid.nclient.transport;

import org.apache.qpid.url.URLHelper;
import org.apache.qpid.url.URLSyntaxException;

import java.util.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public class AMQPConnectionURL implements ConnectionURL
{
    private String _url;
    private String _failoverMethod;
    private HashMap<String, String> _failoverOptions;
    private HashMap<String, String> _options;
    private List<BrokerDetails> _brokers;
    private String _clientName;
    private String _username;
    private String _password;
    private String _virtualHost;

    public AMQPConnectionURL(String fullURL) throws URLSyntaxException
    {
        _url = fullURL;
        _options = new HashMap<String, String>();
        _brokers = new LinkedList<BrokerDetails>();
        _failoverOptions = new HashMap<String, String>();
        
        try
        {
            URI connection = new URI(fullURL);

            if (connection.getScheme() == null || !(connection.getScheme().equalsIgnoreCase(AMQ_PROTOCOL)))
            {
                throw new URISyntaxException(fullURL, "Not an AMQP URL");
            }

            if (connection.getHost() == null || connection.getHost().equals(""))
            {
                String uid = getUniqueClientID();
                if (uid == null)
                {
                    URLHelper.parseError(-1, "Client Name not specified", fullURL);
                }
                else
                {
                    setClientName(uid);
                }

            }
            else
            {
                setClientName(connection.getHost());
            }

            String userInfo = connection.getUserInfo();

            if (userInfo == null)
            {
                //Fix for Java 1.5 which doesn't parse UserInfo for non http URIs
                userInfo = connection.getAuthority();

                if (userInfo != null)
                {
                    int atIndex = userInfo.indexOf('@');

                    if (atIndex != -1)
                    {
                        userInfo = userInfo.substring(0, atIndex);
                    }
                    else
                    {
                        userInfo = null;
                    }
                }

            }

            if (userInfo == null)
            {
                URLHelper.parseError(AMQ_PROTOCOL.length() + 3,
                        "User information not found on url", fullURL);
            }
            else
            {
                parseUserInfo(userInfo);
            }
            String virtualHost = connection.getPath();

            if (virtualHost != null && (!virtualHost.equals("")))
            {
                setVirtualHost(virtualHost);
            }
            else
            {
                int authLength = connection.getAuthority().length();
                int start = AMQ_PROTOCOL.length() + 3;
                int testIndex = start + authLength;
                if (testIndex < fullURL.length() && fullURL.charAt(testIndex) == '?')
                {
                    URLHelper.parseError(start, testIndex - start, "Virtual host found", fullURL);
                }
                else
                {
                    URLHelper.parseError(-1, "Virtual host not specified", fullURL);
                }

            }


            URLHelper.parseOptions(_options, connection.getQuery());

            processOptions();

            //Fragment is #string (not used)
            //System.out.println(connection.getFragment());

        }
        catch (URISyntaxException uris)
        {
            if (uris instanceof URLSyntaxException)
            {
                throw (URLSyntaxException) uris;
            }

            int slash = fullURL.indexOf("\\");

            if (slash == -1)
            {
                URLHelper.parseError(uris.getIndex(), uris.getReason(), uris.getInput());
            }
            else
            {
                if (slash != 0 && fullURL.charAt(slash - 1) == ':')
                {
                    URLHelper.parseError(slash - 2, fullURL.indexOf('?') - slash + 2, "Virtual host looks like a windows path, forward slash not allowed in URL", fullURL);
                }
                else
                {
                    URLHelper.parseError(slash, "Forward slash not allowed in URL", fullURL);
                }
            }

        }
    }
    
    private String getUniqueClientID()
    {
        try
        {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName() + System.currentTimeMillis();
        }
        catch (UnknownHostException e)
        {
            return null;
        }
    }

    private void parseUserInfo(String userinfo) throws URLSyntaxException
    {
        //user info = user:pass

        int colonIndex = userinfo.indexOf(':');

        if (colonIndex == -1)
        {
            URLHelper.parseError(AMQ_PROTOCOL.length() + 3, userinfo.length(),
                    "Null password in user information not allowed.", _url);
        }
        else
        {
            setUsername(userinfo.substring(0, colonIndex));
            setPassword(userinfo.substring(colonIndex + 1));
        }

    }

    private void processOptions() throws URLSyntaxException
    {
        if (_options.containsKey(OPTIONS_BROKERLIST))
        {
            String brokerlist = _options.get(OPTIONS_BROKERLIST);

            //brokerlist tcp://host:port?option='value',option='value';vm://:3/virtualpath?option='value'
            StringTokenizer st = new StringTokenizer(brokerlist, "" + URLHelper.BROKER_SEPARATOR);

            while (st.hasMoreTokens())
            {
                String broker = st.nextToken();

                _brokers.add(new AMQPBrokerDetails(broker));
            }

            _options.remove(OPTIONS_BROKERLIST);
        }

        if (_options.containsKey(OPTIONS_FAILOVER))
        {
            String failover = _options.get(OPTIONS_FAILOVER);

            // failover='method?option='value',option='value''

            int methodIndex = failover.indexOf('?');

            if (methodIndex > -1)
            {
                _failoverMethod = failover.substring(0, methodIndex);
                URLHelper.parseOptions(_failoverOptions, failover.substring(methodIndex + 1));
            }
            else
            {
                _failoverMethod = failover;
            }

            _options.remove(OPTIONS_FAILOVER);
        }
    }

    public String getURL()
    {
        return _url;
    }

    public String getFailoverMethod()
    {
        return _failoverMethod;
    }

    public String getFailoverOption(String key)
    {
        return _failoverOptions.get(key);
    }

    public void setFailoverOption(String key, String value)
    {
        _failoverOptions.put(key, value);
    }

    public int getBrokerCount()
    {
        return _brokers.size();
    }

    public BrokerDetails getBrokerDetails(int index)
    {
        if (index < _brokers.size())
        {
            return _brokers.get(index);
        }
        else
        {
            return null;
        }
    }

    public void addBrokerDetails(BrokerDetails broker)
    {
        if (!(_brokers.contains(broker)))
        {
            _brokers.add(broker);
        }
    }

    public List<BrokerDetails> getAllBrokerDetails()
    {
        return _brokers;
    }

    public String getClientName()
    {
        return _clientName;
    }

    public void setClientName(String clientName)
    {
        _clientName = clientName;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public String getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(String virtuaHost)
    {
        _virtualHost = virtuaHost;
    }

    public String getOption(String key)
    {
        return _options.get(key);
    }

    public void setOption(String key, String value)
    {
        _options.put(key, value);
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(AMQ_PROTOCOL);
        sb.append("://");

        if (_username != null)
        {
            sb.append(_username);

            if (_password != null)
            {
                sb.append(':');
                sb.append(_password);
            }

            sb.append('@');
        }

        sb.append(_clientName);

        sb.append(_virtualHost);

        sb.append(optionsToString());

        return sb.toString();
    }

    private String optionsToString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append("?" + OPTIONS_BROKERLIST + "='");

        for (BrokerDetails service : _brokers)
        {
            sb.append(service.toString());
            sb.append(';');
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append("'");

        if (_failoverMethod != null)
        {
            sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            sb.append(OPTIONS_FAILOVER + "='");
            sb.append(_failoverMethod);
            sb.append(URLHelper.printOptions(_failoverOptions));
            sb.append("'");
        }

        return sb.toString();
    }


    public static void main(String[] args) throws URLSyntaxException
    {

        String url2 = "amqp://ritchiem:bob@temp?brokerlist='tcp://localhost:5672;jcp://fancyserver:3000/',failover='roundrobin'";
        //"amqp://user:pass@clientid/virtualhost?brokerlist='tcp://host:1?option1=\'value\',option2=\'value\';vm://:3?option1=\'value\'',failover='method?option1=\'value\',option2='value''";

        //ConnectionURL connectionurl2 = new AMQConnectionURL(url2);

        System.out.println(url2);
        //System.out.println(connectionurl2);

    }

}
