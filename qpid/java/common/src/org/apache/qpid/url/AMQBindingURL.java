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
package org.apache.qpid.url;

import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.URLHelper;
import org.apache.qpid.exchange.ExchangeDefaults;

import java.util.HashMap;
import java.net.URI;
import java.net.URISyntaxException;

public class AMQBindingURL implements BindingURL
{
    String _url;
    String _exchangeClass;
    String _exchangeName;
    String _destinationName;
    String _queueName;
    private HashMap<String, String> _options;


    public AMQBindingURL(String url) throws URLSyntaxException
    {
        //format:
        // <exch_class>://<exch_name>/[<destination>]/[<queue>]?<option>='<value>'[,<option>='<value>']*

        _url = url;
        _options = new HashMap<String, String>();

        parseBindingURL();
    }

    private void parseBindingURL() throws URLSyntaxException
    {
        try
        {
            URI connection = new URI(_url);

            String exchangeClass = connection.getScheme();

            if (exchangeClass == null)
            {
                _url = ExchangeDefaults.DIRECT_EXCHANGE_CLASS + "://" +
                        ExchangeDefaults.DIRECT_EXCHANGE_NAME + "//" + _url;
                //URLHelper.parseError(-1, "Exchange Class not specified.", _url);
                parseBindingURL();
                return;
            }
            else
            {
                setExchangeClass(exchangeClass);
            }

            String exchangeName = connection.getHost();

            if (exchangeName == null)
            {
                URLHelper.parseError(-1, "Exchange Name not specified.", _url);
            }
            else
            {
                setExchangeName(exchangeName);
            }

            if (connection.getPath() == null ||
                    connection.getPath().equals(""))
            {
                URLHelper.parseError(_url.indexOf(_exchangeName) + _exchangeName.length(),
                        "Destination or Queue requried", _url);
            }
            else
            {
                int slash = connection.getPath().indexOf("/", 1);
                if (slash == -1)
                {
                    URLHelper.parseError(_url.indexOf(_exchangeName) + _exchangeName.length(),
                            "Destination requried", _url);
                }
                else
                {
                    String path = connection.getPath();
                    setDestinationName(path.substring(1, slash));

                    setQueueName(path.substring(slash + 1));

                }
            }

            URLHelper.parseOptions(_options, connection.getQuery());

            processOptions();

            //Fragment is #string (not used)
            //System.out.println(connection.getFragment());

        }
        catch (URISyntaxException uris)
        {

            URLHelper.parseError(uris.getIndex(), uris.getReason(), uris.getInput());

        }
    }

    private void processOptions()
    {
        //this is where we would parse any options that needed more than just storage.
    }

    public String getURL()
    {
        return _url;
    }

    public String getExchangeClass()
    {
        return _exchangeClass;
    }

    public void setExchangeClass(String exchangeClass)
    {
        _exchangeClass = exchangeClass;
    }

    public String getExchangeName()
    {
        return _exchangeName;
    }

    public void setExchangeName(String name)
    {
        _exchangeName = name;

        if (name.equals(ExchangeDefaults.TOPIC_EXCHANGE_NAME))
        {
            setOption(BindingURL.OPTION_EXCLUSIVE, "true");
        }
    }

    public String getDestinationName()
    {
        return _destinationName;
    }

    public void setDestinationName(String name)
    {
        _destinationName = name;
    }

    public String getQueueName()
    {
        if (_exchangeClass.equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
        {
            if (Boolean.parseBoolean(getOption(OPTION_DURABLE)))
            {
                if (containsOption(BindingURL.OPTION_CLIENTID) && containsOption(BindingURL.OPTION_SUBSCRIPTION))
                {
                    return getOption(BindingURL.OPTION_CLIENTID + ":" + BindingURL.OPTION_SUBSCRIPTION);
                }
                else
                {
                    return getDestinationName();
                }
            }
            else
            {
                return getDestinationName();
            }
        }
        else
        {
            return _queueName;
        }
    }

    public void setQueueName(String name)
    {
        _queueName = name;
    }

    public String getOption(String key)
    {
        return _options.get(key);
    }

    public void setOption(String key, String value)
    {
        _options.put(key, value);
    }

    public boolean containsOption(String key)
    {
        return _options.containsKey(key);
    }

    public String getRoutingKey()
    {
        if (_exchangeClass.equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
        {
            return getQueueName();
        }

        if (containsOption(BindingURL.OPTION_ROUTING_KEY))
        {
            return getOption(OPTION_ROUTING_KEY);
        }

        return getDestinationName();
    }

    public void setRoutingKey(String key)
    {
        setOption(OPTION_ROUTING_KEY, key);
    }


    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(_exchangeClass);
        sb.append("://");
        sb.append(_exchangeName);
        sb.append('/');
        sb.append(_destinationName);
        sb.append('/');
        sb.append(_queueName);

        sb.append(URLHelper.printOptions(_options));
        return sb.toString();
    }

    public static void main(String args[]) throws URLSyntaxException
    {
        String url = "exchangeClass://exchangeName/Destination/Queue?option='value',option2='value2'";

        AMQBindingURL dest = new AMQBindingURL(url);

        System.out.println(url);
        System.out.println(dest);

    }

}