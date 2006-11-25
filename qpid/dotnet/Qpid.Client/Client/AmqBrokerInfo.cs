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
using System;
using System.Collections;
using System.Text;
using Qpid.Client.qms;

namespace Qpid.Client
{
    public class AmqBrokerInfo : BrokerInfo
    {
        private string _host = "localhost";
        private int _port = 5672;
        private string _transport = "amqp";
        
        public readonly string URL_FORMAT_EXAMPLE =
            "<transport>://<hostname>[:<port Default=\""+BrokerDetailsConstants.DEFAULT_PORT+"\">][?<option>='<value>'[,<option>='<value>']]";

        public const long DEFAULT_CONNECT_TIMEOUT = 30000L;

        private Hashtable _options = new Hashtable();
        
        public AmqBrokerInfo()
        {
        }

        // TODO: port URL parsing.
        public AmqBrokerInfo(string url)
        {
            throw new NotImplementedException();
//            this();
//            // URL should be of format tcp://host:port?option='value',option='value'
//            try
//            {
//                URI connection = new URI(url);
//
//                string transport = connection.getScheme();
//
//                // Handles some defaults to minimise changes to existing broker URLS e.g. localhost
//                if (transport != null)
//                {
//                    //todo this list of valid transports should be enumerated somewhere
//                    if ((!(transport.equalsIgnoreCase("vm") ||
//                            transport.equalsIgnoreCase("tcp"))))
//                    {
//                        if (transport.equalsIgnoreCase("localhost"))
//                        {
//                            connection = new URI(DEFAULT_TRANSPORT + "://" + url);
//                            transport = connection.getScheme();
//                        }
//                        else
//                        {
//                            if (url.charAt(transport.length()) == ':' && url.charAt(transport.length()+1) != '/' )
//                            {
//                                //Then most likely we have a host:port value
//                                connection = new URI(DEFAULT_TRANSPORT + "://" + url);
//                                transport = connection.getScheme();
//                            }
//                            else
//                            {
//                                URLHelper.parseError(0, transport.length(), "Unknown transport", url);
//                            }
//                        }
//                    }
//                }
//                else
//                {
//                    //Default the transport
//                    connection = new URI(DEFAULT_TRANSPORT + "://" + url);
//                    transport = connection.getScheme();
//                }
//
//                if (transport == null)
//                {
//                    URLHelper.parseError(-1, "Unknown transport:'" + transport + "'" +
//                            " In broker URL:'" + url + "' Format: " + URL_FORMAT_EXAMPLE, "");
//                }
//
//                setTransport(transport);
//
//                string host = connection.getHost();
//
//                // Fix for Java 1.5
//                if (host == null)
//                {
//                    host = "";
//                }
//
//                setHost(host);
//
//                int port = connection.getPort();
//
//                if (port == -1)
//                {
//                    // Another fix for Java 1.5 URI handling
//                    string auth = connection.getAuthority();
//
//                    if (auth != null && auth.startsWith(":"))
//                    {
//                        setPort(Integer.parseInt(auth.substring(1)));
//                    }
//                    else
//                    {
//                        setPort(DEFAULT_PORT);
//                    }
//                }
//                else
//                {
//                    setPort(port);
//                }
//
//                string querystring = connection.getQuery();
//
//                URLHelper.parseOptions(_options, querystring);
//
//                //Fragment is #string (not used)
//            }
//            catch (URISyntaxException uris)
//            {
//                if (uris instanceof URLSyntaxException)
//                {
//                    throw (URLSyntaxException) uris;
//                }
//
//                URLHelper.parseError(uris.getIndex(), uris.getReason(), uris.getInput());
//            }
        }

        public AmqBrokerInfo(string transport, string host, int port, bool useSSL) : this()
        {
            _transport = transport;
            _host = host;
            _port = port;

            if (useSSL)
            {
                setOption(BrokerDetailsConstants.OPTIONS_SSL, "true");
            }
        }

        public string getHost()
        {
            return _host;
        }

        public void setHost(string _host)
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

        public string getTransport()
        {
            return _transport;
        }

        public void setTransport(string _transport)
        {
            this._transport = _transport;
        }

        public string getOption(string key)
        {
            return (string)_options[key];
        }

        public void setOption(string key, string value)
        {
            _options[key] = value;
        }

        public long getTimeout()
        {
            if (_options.ContainsKey(BrokerDetailsConstants.OPTIONS_CONNECT_TIMEOUT))
            {
                try
                {
                    return long.Parse((string)_options[BrokerDetailsConstants.OPTIONS_CONNECT_TIMEOUT]);
                }
                catch (FormatException nfe)
                {
                    //Do nothing as we will use the default below.
                }
            }

            return BrokerDetailsConstants.DEFAULT_CONNECT_TIMEOUT;
        }

        public void setTimeout(long timeout)
        {
            setOption(BrokerDetailsConstants.OPTIONS_CONNECT_TIMEOUT, timeout.ToString());
        }

        public override string ToString()
        {
            StringBuilder sb = new StringBuilder();

            sb.Append(_transport);
            sb.Append("://");

            if (!(_transport.ToLower().Equals("vm")))
            {
                sb.Append(_host);
            }

            sb.Append(':');
            sb.Append(_port);

            // XXX
//            sb.Append(printOptionsURL());

            return sb.ToString();
        }

        public override bool Equals(object o)
        {
            if (!(o is BrokerInfo))
            {
                return false;
            }

            BrokerInfo bd = (BrokerInfo) o;

            return StringEqualsIgnoreCase(_host, bd.getHost()) &&
                   (_port == bd.getPort()) &&
                   StringEqualsIgnoreCase(_transport, bd.getTransport()) &&
                   (useSSL() == bd.useSSL());

            //todo do we need to compare all the options as well?
        }
        
        // TODO: move to util class.
        private bool StringEqualsIgnoreCase(string one, string two)
        {
            return one.ToLower().Equals(two.ToLower());
        }

//        private string printOptionsURL()
//        {
//            stringBuffer optionsURL = new stringBuffer();
//
//            optionsURL.Append('?');
//
//            if (!(_options.isEmpty()))
//            {
//
//                for (string key : _options.keySet())
//                {
//                    optionsURL.Append(key);
//
//                    optionsURL.Append("='");
//
//                    optionsURL.Append(_options.get(key));
//
//                    optionsURL.Append("'");
//
//                    optionsURL.Append(URLHelper.DEFAULT_OPTION_SEPERATOR);
//                }
//            }
//
//            //remove the extra DEFAULT_OPTION_SEPERATOR or the '?' if there are no options
//            optionsURL.deleteCharAt(optionsURL.length() - 1);
//
//            return optionsURL.tostring();
//        }

        public bool useSSL()
        {
            // To be friendly to users we should be case insensitive.
            // or simply force users to conform to OPTIONS_SSL
            // todo make case insensitive by trying ssl Ssl sSl ssL SSl SsL sSL SSL

            if (_options.ContainsKey(BrokerDetailsConstants.OPTIONS_SSL))
            {
                return StringEqualsIgnoreCase((string)_options[BrokerDetailsConstants.OPTIONS_SSL], "true");
            }

            return false;
        }

        public void useSSL(bool ssl)
        {
            setOption(BrokerDetailsConstants.OPTIONS_SSL, ssl.ToString());
        }
    }
}