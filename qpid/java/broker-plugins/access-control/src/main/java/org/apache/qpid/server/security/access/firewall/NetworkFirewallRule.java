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
package org.apache.qpid.server.security.access.firewall;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkFirewallRule implements FirewallRule
{
    private static final Logger _logger = LoggerFactory.getLogger(NetworkFirewallRule.class);

    private List<InetNetwork> _networks;

    public NetworkFirewallRule(String... networks)
    {
        _networks = new ArrayList<InetNetwork>();
        for (int i = 0; i < networks.length; i++)
        {
            String network = networks[i];
            try
            {
                InetNetwork inetNetwork = InetNetwork.getFromString(network);
                if (!_networks.contains(inetNetwork))
                {
                    _networks.add(inetNetwork);
                }
            }
            catch (java.net.UnknownHostException uhe)
            {
                _logger.error("Cannot resolve address: " + network, uhe);
            }
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Created " + this);
        }
    }

    @Override
    public boolean matches(InetAddress ip)
    {
        for (InetNetwork network : _networks)
        {
            if (network.contains(ip))
            {
                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Client address " + ip + " matches configured network " + network);
                }
                return true;
            }
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Client address " + ip + " does not match any configured networks");
        }

        return false;
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder().append(_networks).toHashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }
        if (obj.getClass() != getClass())
        {
            return false;
        }
        NetworkFirewallRule rhs = (NetworkFirewallRule) obj;
        return new EqualsBuilder().append(_networks, rhs._networks).isEquals();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append(_networks).toString();
    }
}
