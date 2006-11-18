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
package org.apache.qpid.client;

import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.url.URLHelper;
import org.apache.qpid.url.URLSyntaxException;

import java.util.HashMap;
import java.net.URISyntaxException;
import java.net.URI;

public class AMQBrokerDetails implements BrokerDetails
{
    private String _host;
    private int _port;
    private String _transport;

    private HashMap<String, String> _options;

    public AMQBrokerDetails()
    {
        _options = new HashMap<String, String>();
    }

    public AMQBrokerDetails(String url) throws URLSyntaxException
    {
        this();
        // URL should be of format tcp://host:port?option='value',option='value'
        try
        {
            URI connection = new URI(url);

            String transport = connection.getScheme();

            // Handles some defaults to minimise changes to existing broker URLS e.g. localhost
            if (transport != null)
            {
                //todo this list of valid transports should be enumerated somewhere
                if ((!(transport.equalsIgnoreCase("vm") ||
                        transport.equalsIgnoreCase("tcp"))))
                {
                    if (transport.equalsIgnoreCase("localhost"))
                    {
                        connection = new URI(DEFAULT_TRANSPORT + "://" + url);
                        transport = connection.getScheme();
                    }
                    else
                    {
                        if (url.charAt(transport.length()) == ':' && url.charAt(transport.length()+1) != '/' )
                        {
                            //Then most likely we have a host:port value
                            connection = new URI(DEFAULT_TRANSPORT + "://" + url);
                            transport = connection.getScheme();
                        }
                        else
                        {
                            URLHelper.parseError(0, transport.length(), "Unknown transport", url);
                        }
                    }
                }
            }
            else
            {
                //Default the transport
                connection = new URI(DEFAULT_TRANSPORT + "://" + url);
                transport = connection.getScheme();
            }

            if (transport == null)
            {
                URLHelper.parseError(-1, "Unknown transport:'" + transport + "'" +
                        " In broker URL:'" + url + "' Format: " + URL_FORMAT_EXAMPLE, "");
            }

            setTransport(transport);

            String host = connection.getHost();

            // Fix for Java 1.5
            if (host == null)
            {
                host = "";
            }

            setHost(host);

            int port = connection.getPort();

            if (port == -1)
            {
                // Another fix for Java 1.5 URI handling
                String auth = connection.getAuthority();

                if (auth != null && auth.startsWith(":"))
                {
                    setPort(Integer.parseInt(auth.substring(1)));
                }
                else
                {
                    setPort(DEFAULT_PORT);
                }
            }
            else
            {
                setPort(port);
            }

            String queryString = connection.getQuery();

            URLHelper.parseOptions(_options, queryString);

            //Fragment is #string (not used)
        }
        catch (URISyntaxException uris)
        {
            if (uris instanceof URLSyntaxException)
            {
                throw (URLSyntaxException) uris;
            }

            URLHelper.parseError(uris.getIndex(), uris.getReason(), uris.getInput());
        }
    }

    public AMQBrokerDetails(String host, int port, boolean useSSL)
    {
        _host = host;
        _port = port;

        if (useSSL)
        {
            setOption(OPTIONS_SSL, "true");
        }
    }

    public String getHost()
    {
        return _host;
    }

    public void setHost(String _host)
    {
        this._host = _host;
    }

    public int getPort()
    {
        return _port;
    }

    public void setPort(int _port)
    {
        this._port = _port;
    }

    public String getTransport()
    {
        return _transport;
    }

    public void setTransport(String _transport)
    {
        this._transport = _transport;
    }


    public String getOption(String key)
    {
        return _options.get(key);
    }

    public void setOption(String key, String value)
    {
        _options.put(key, value);
    }

    public long getTimeout()
    {
        if (_options.containsKey(OPTIONS_CONNECT_TIMEOUT))
        {
            try
            {
                return Long.parseLong(_options.get(OPTIONS_CONNECT_TIMEOUT));
            }
            catch (NumberFormatException nfe)
            {
                //Do nothing as we will use the default below.
            }
        }

        return BrokerDetails.DEFAULT_CONNECT_TIMEOUT;
    }

    public void setTimeout(long timeout)
    {
        setOption(OPTIONS_CONNECT_TIMEOUT, Long.toString(timeout));
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(_transport);
        sb.append("://");

        if (!(_transport.equalsIgnoreCase("vm")))
        {
            sb.append(_host);
        }

        sb.append(':');
        sb.append(_port);

        sb.append(printOptionsURL());

        return sb.toString();
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof BrokerDetails))
        {
            return false;
        }

        BrokerDetails bd = (BrokerDetails) o;

        return _host.equalsIgnoreCase(bd.getHost()) &&
                (_port == bd.getPort()) &&
                _transport.equalsIgnoreCase(bd.getTransport()) &&
                (useSSL() == bd.useSSL());

        //todo do we need to compare all the options as well?
    }

    private String printOptionsURL()
    {
        StringBuffer optionsURL = new StringBuffer();

        optionsURL.append('?');

        if (!(_options.isEmpty()))
        {

            for (String key : _options.keySet())
            {
                optionsURL.append(key);

                optionsURL.append("='");

                optionsURL.append(_options.get(key));

                optionsURL.append("'");

                optionsURL.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }
        }

        //remove the extra DEFAULT_OPTION_SEPERATOR or the '?' if there are no options
        optionsURL.deleteCharAt(optionsURL.length() - 1);

        return optionsURL.toString();
    }

    public boolean useSSL()
    {
        // To be friendly to users we should be case insensitive.
        // or simply force users to conform to OPTIONS_SSL
        // todo make case insensitive by trying ssl Ssl sSl ssL SSl SsL sSL SSL

        if (_options.containsKey(OPTIONS_SSL))
        {
            return _options.get(OPTIONS_SSL).equalsIgnoreCase("true");
        }

        return USE_SSL_DEFAULT;
    }

    public void useSSL(boolean ssl)
    {
        setOption(OPTIONS_SSL, Boolean.toString(ssl));
    }


}
