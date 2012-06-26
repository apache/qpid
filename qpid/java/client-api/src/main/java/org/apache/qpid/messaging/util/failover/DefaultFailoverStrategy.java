/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.messaging.util.failover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ConnectionString;
import org.apache.qpid.messaging.internal.FailoverStrategy;

public class DefaultFailoverStrategy implements FailoverStrategy
{
    ConnectionString[] _reconnectURLs;

    /** seconds (give up and report failure after specified time) */
    private long _reconnectTimeout = 1000;

    /** n (give up and report failure after specified number of attempts) */
    private int  _reconnectLimit = 1;

    /** seconds (initial delay between failed reconnection attempts) */
    private long _reconnectIntervalMin = 1000;

    /** seconds (maximum delay between failed reconnection attempts) */
    private long _reconnectIntervalMax = 1000;

    private ConnectionString _currentUrl;

    /** reconnection attemps */
    private int _attempts = 0;

    /** Index for retrieving a URL from _reconnectURLs */
    private int _index = 0;

    /* quick and dirty impl for experimentation */
    public DefaultFailoverStrategy(String url, Map<String,Object> map)
    {
        map = map == null ? Collections.EMPTY_MAP : map;
        _currentUrl = new ConnectionStringImpl(url,map);
        // read the map and fill in the above fields.
        if (map.containsKey("reconnect_urls"))
        {
            String[] urls = ((String)map.get("reconnect_urls")).split(",");
            _reconnectURLs = new ConnectionString[urls.length];
            for(int i=0; i<urls.length; i++)
            {
                _reconnectURLs[i] = new ConnectionStringImpl(urls[i],map);
            }
        }
        else
        {
            _reconnectURLs = new ConnectionString[]{_currentUrl};
        }
        _reconnectLimit = _reconnectURLs.length;
    }

    @Override
    public boolean failoverAllowed()
    {
        return (_attempts < _reconnectLimit);
    }

    @Override
    public ConnectionString getNextConnectionString()
    {
        // quick implementation for experimentation, ignoring timeouts, reconnect intervals etc..
        // the _index will wrap around like a circular buffer
        _attempts++;
        if (_index == _reconnectURLs.length)
        {
            _index = 0;
        }
        _currentUrl = _reconnectURLs[_index++];
        return _currentUrl;
    }

    @Override
    public ConnectionString getCurrentConnectionString()
    {
        return _currentUrl;
    }

    @Override
    public void connectionAttained(ConnectionInternal conn)
    {
        _attempts = 0;
    }

    class ConnectionStringImpl implements ConnectionString
    {
        private final String _url;
        private final Map<String, Object> _options;

        public ConnectionStringImpl(String url, Map<String, Object> options)
        {
            _url = url;
            _options = options;
        }

        public String getUrl()
        {
            return _url;
        }

        public Map<String, Object> getOptions()
        {
            return _options;
        }

    }
}
