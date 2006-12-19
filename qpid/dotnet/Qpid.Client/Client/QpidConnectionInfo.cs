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
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using log4net;
using Qpid.Client.qms;

namespace Qpid.Client
{

    public class URLHelper
    {
        public static char DEFAULT_OPTION_SEPERATOR = '&';
        public static char ALTERNATIVE_OPTION_SEPARATOR = ',';
        public static char BROKER_SEPARATOR = ';';

        /// <summary>
        /// 
        /// </summary>
        /// <param name="optionMap"></param>
        /// <param name="options"></param>
        public static void parseOptions(IDictionary optionMap, string options)
        {
            //options looks like this
            //brokerlist='tcp://host:port?option='value',option='value';vm://:3/virtualpath?option='value'',failover='method?option='value',option='value''

            if (options == null || options.IndexOf('=') == -1)
            {
                return;
            }

            int optionIndex = options.IndexOf('=');

            String option = options.Substring(0, optionIndex);

            int length = options.Length;

            int nestedQuotes = 0;

            // to store index of final "'"
            int valueIndex = optionIndex;

            //Walk remainder of url.
            while (nestedQuotes > 0 || valueIndex < length)
            {
                valueIndex++;

                if (valueIndex >= length)
                {
                    break;
                }

                if (options[valueIndex] == '\'')
                {
                    if (valueIndex + 1 < options.Length)
                    {
                        if (options[valueIndex + 1] == DEFAULT_OPTION_SEPERATOR ||
                                options[valueIndex + 1] == ALTERNATIVE_OPTION_SEPARATOR ||
                                options[valueIndex + 1] == BROKER_SEPARATOR ||
                                options[valueIndex + 1] == '\'')
                        {
                            nestedQuotes--;
                            //                        System.out.println(
                            //                                options + "\n" + "-" + nestedQuotes + ":" + getPositionString(valueIndex - 2, 1));
                            if (nestedQuotes == 0)
                            {
                                //We've found the value of an option
                                break;
                            }
                        }
                        else
                        {
                            nestedQuotes++;
                            //                        System.out.println(
                            //                                options + "\n" + "+" + nestedQuotes + ":" + getPositionString(valueIndex - 2, 1));
                        }
                    }
                    else
                    {
                        // We are at the end of the string
                        // Check to see if we are corectly closing quotes
                        if (options[valueIndex] == '\'')
                        {
                            nestedQuotes--;
                        }

                        break;
                    }
                }
            }

            if (nestedQuotes != 0 || valueIndex < (optionIndex + 2))
            {
                int sepIndex = 0;

                //Try and identify illegal separator character
                if (nestedQuotes > 1)
                {
                    for (int i = 0; i < nestedQuotes; i++)
                    {
                        sepIndex = options.IndexOf('\'', sepIndex);
                        sepIndex++;
                    }
                }

                if (sepIndex >= options.Length || sepIndex == 0)
                {
                    parseError(valueIndex, "Unterminated option", options);
                }
                else
                {
                    parseError(sepIndex, "Unterminated option. Possible illegal option separator:'" +
                            options[sepIndex] + "'", options);
                }
            }

            // optionIndex +2 to skip "='"
            int sublen = valueIndex - (optionIndex + 2);
            String value = options.Substring(optionIndex + 2, sublen);

            optionMap.Add(option, value);

            if (valueIndex < (options.Length - 1))
            {
                //Recurse to get remaining options
                parseOptions(optionMap, options.Substring(valueIndex + 2));
            }
        }


        public static void parseError(int index, String error, String url)
        {
            parseError(index, 1, error, url);
        }

        public static void parseError(int index, int length, String error, String url)
        {
            throw new UrlSyntaxException(url, error, index, length);
        }

        public static String printOptions(Hashtable options)
        {
            if (options.Count == 0)
            {
                return "";
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                sb.Append('?');
                foreach (String key in options.Keys)
                {
                    sb.Append(key);

                    sb.Append("='");

                    sb.Append(options[key]);

                    sb.Append("'");
                    sb.Append(DEFAULT_OPTION_SEPERATOR);
                }

                sb.Remove(sb.Length - 1, 1);
                //                sb.deleteCharAt(sb.length() - 1);

                return sb.ToString();
            }
        }

    }

    public class QpidConnectionUrl
    {
        internal static ConnectionInfo FromUrl(string fullURL)
        {
            //_url = fullURL;
            ConnectionInfo connectionInfo = new QpidConnectionInfo();


            //            _options = new HashMap<String, String>();
            //            _brokers = new LinkedList();
            //            _failoverOptions = new HashMap<String, String>();

            // Connection URL format
            //amqp://[user:pass@][clientid]/virtualhost?brokerlist='tcp://host:port?option=\'value\',option=\'value\';vm://:3/virtualpath?option=\'value\'',failover='method?option=\'value\',option='value''"
            // Options are of course optional except for requiring a single broker in the broker list.
            try
            {
                Uri connection = new Uri(fullURL);

                if (connection.Scheme == null || !(connection.Scheme.Equals(ConnectionUrlConstants.AMQ_PROTOCOL)))
                {
                    throw new UrlSyntaxException(fullURL, "Not an AMQP URL");
                }

                if (connection.Host != null && connection.Host.Length > 0 && !connection.Host.Equals("default"))
                {
                    connectionInfo.SetClientName(connection.Host);
                }

                String userInfo = connection.UserInfo;
                if (userInfo == null || userInfo.Length == 0)
                {
                    URLHelper.parseError(ConnectionUrlConstants.AMQ_PROTOCOL.Length + 3,
                            "User information not found on url", fullURL);
                }
                else
                {
                    parseUserInfo(userInfo, fullURL, connectionInfo);
                }
                String virtualHost = connection.AbsolutePath; // XXX: is AbsolutePath corrrect?

                if (virtualHost != null && (!virtualHost.Equals("")))
                {
                    connectionInfo.SetVirtualHost(virtualHost);
                }
                else
                {
                    int authLength = connection.Authority.Length;
                    int start = ConnectionUrlConstants.AMQ_PROTOCOL.Length + 3;
                    int testIndex = start + authLength;
                    if (testIndex < fullURL.Length && fullURL[testIndex] == '?')
                    {
                        URLHelper.parseError(start, testIndex - start, "Virtual host found", fullURL);
                    }
                    else
                    {
                        URLHelper.parseError(-1, "Virtual host not specified", fullURL);
                    }

                }

                QpidConnectionInfo qci = (QpidConnectionInfo)connectionInfo;
                string query = connection.Query;
                if (query[0] == '?') query = query.Substring(1);
                URLHelper.parseOptions(qci.GetOptions(), query);

                processOptions(connectionInfo);

                //Fragment is #string (not used)
                //System.out.println(connection.getFragment());
                return connectionInfo;
            }
            catch (UriFormatException uris)
            {
                throw uris;
                //                if (uris is UrlSyntaxException)
                //                {
                //                    throw uris;
                //                }
                //
                //                int slash = fullURL.IndexOf("\\");
                //
                //                if (slash == -1)
                //                {
                //                    URLHelper.parseError(uris.GetIndex(), uris.getReason(), uris.getInput());
                //                }
                //                else
                //                {
                //                    if (slash != 0 && fullURL.charAt(slash - 1) == ':')
                //                    {
                //                        URLHelper.parseError(slash - 2, fullURL.indexOf('?') - slash + 2, "Virtual host looks like a windows path, forward slash not allowed in URL", fullURL);
                //                    }
                //                    else
                //                    {
                //                        URLHelper.parseError(slash, "Forward slash not allowed in URL", fullURL);
                //                    }
                //                }
            }
        }

        private static void parseUserInfo(String userinfo, string fullUrl, ConnectionInfo connectionInfo)
        {
            //user info = user:pass

            int colonIndex = userinfo.IndexOf(':');

            if (colonIndex == -1)
            {
                URLHelper.parseError(ConnectionUrlConstants.AMQ_PROTOCOL.Length + 3,
                    userinfo.Length, "Null password in user information not allowed.", fullUrl);
            }
            else
            {
                connectionInfo.setUsername(userinfo.Substring(0, colonIndex));
                connectionInfo.SetPassword(userinfo.Substring(colonIndex + 1));
            }
        }

        private static void processOptions(ConnectionInfo connectionInfo)
        {
            string brokerlist = connectionInfo.GetOption(ConnectionUrlConstants.OPTIONS_BROKERLIST);
            if (brokerlist != null)
            {
                //brokerlist tcp://host:port?option='value',option='value';vm://:3/virtualpath?option='value'
                Regex splitter = new Regex("" + URLHelper.BROKER_SEPARATOR);

                foreach (string broker in splitter.Split(brokerlist))
                {
                    connectionInfo.AddBrokerInfo(new AmqBrokerInfo(broker));
                }

                connectionInfo.SetOption(ConnectionUrlConstants.OPTIONS_BROKERLIST, null);
                //                _options.remove(OPTIONS_BROKERLIST);
            }

            string failover = connectionInfo.GetOption(ConnectionUrlConstants.OPTIONS_FAILOVER);
            if (failover != null)
            {
                // failover='method?option='value',option='value''

                int methodIndex = failover.IndexOf('?');

                if (methodIndex > -1)
                {
                    connectionInfo.SetFailoverMethod(failover.Substring(0, methodIndex));
                    QpidConnectionInfo qpidConnectionInfo = (QpidConnectionInfo)connectionInfo;
                    URLHelper.parseOptions(qpidConnectionInfo.GetFailoverOptions(),
                        failover.Substring(methodIndex + 1));
                }
                else
                {
                    connectionInfo.SetFailoverMethod(failover);
                }

                connectionInfo.SetOption(ConnectionUrlConstants.OPTIONS_FAILOVER, null);
                //                _options.remove(OPTIONS_FAILOVER);
            }
        }

        internal static ConnectionInfo FromUri(Uri uri)
        {
            return null; // FIXME

        }
    }

    public class QpidConnectionInfo : ConnectionInfo
    {
        string _username = "guest";
        string _password = "guest";
        string _virtualHost = "/default";

        string _failoverMethod = null;
        IDictionary _failoverOptions = new Hashtable();
        IDictionary _options = new Hashtable();
        IList _brokerInfos = new ArrayList(); // List<BrokerInfo>
        string _clientName = String.Format("{0}{1:G}", Dns.GetHostName(), DateTime.Now.Ticks);

        public IDictionary GetFailoverOptions()
        {
            return _failoverOptions;
        }

        public IDictionary GetOptions()
        {
            return _options;
        }

        public static ConnectionInfo FromUrl(String url)
        {
            return QpidConnectionUrl.FromUrl(url);
        }

        public string AsUrl()
        {
            string result = "amqp://";
            foreach (BrokerInfo info in _brokerInfos)
            {
                result += info.ToString();
            }
            return result;

        }

        public string GetFailoverMethod()
        {
            return _failoverMethod;
        }

        public void SetFailoverMethod(string failoverMethod)
        {
            _failoverMethod = failoverMethod;
        }

        public string GetFailoverOption(string key)
        {
            return (string)_failoverOptions[key];
        }

        public int GetBrokerCount()
        {
            return _brokerInfos.Count;
        }

        public BrokerInfo GetBrokerInfo(int index)
        {
            return (BrokerInfo)_brokerInfos[index];
        }

        public void AddBrokerInfo(BrokerInfo brokerInfo)
        {
            if (!_brokerInfos.Contains(brokerInfo))
            {
                _brokerInfos.Add(brokerInfo);
            }
        }

        public IList GetAllBrokerInfos()
        {
            return _brokerInfos;
        }

        public string GetClientName()
        {
            return _clientName;
        }

        public void SetClientName(string clientName)
        {
            _clientName = clientName;
        }

        public string GetUsername()
        {
            return _username;
        }

        public void setUsername(string username)
        {
            _username = username;
        }

        public string GetPassword()
        {
            return _password;
        }

        public void SetPassword(string password)
        {
            _password = password;
        }

        public string GetVirtualHost()
        {
            return _virtualHost;
        }

        public void SetVirtualHost(string virtualHost)
        {
            _virtualHost = virtualHost;
        }

        public string GetOption(string key)
        {
            return (string)_options[key];
        }

        public void SetOption(string key, string value)
        {
            _options[key] = value;
        }

        public override string ToString()
        {
            return AsUrl();
        }
    }
}
