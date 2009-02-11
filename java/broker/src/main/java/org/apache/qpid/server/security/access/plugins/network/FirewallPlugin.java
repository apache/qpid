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
package org.apache.qpid.server.security.access.plugins.network;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.protocol.AMQMinaProtocolSession;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.security.access.plugins.AbstractACLPlugin;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.util.NetMatcher;

import sun.net.util.IPAddressUtil;

public class FirewallPlugin extends AbstractACLPlugin
{

    public class FirewallRule
    {

        private AuthzResult _access;
        private NetMatcher _network;
        private Pattern _hostnamePattern;

        public FirewallRule(String access, String network, String hostname)
        {
            _access = (access.equals("allow")) ? AuthzResult.ALLOWED : AuthzResult.DENIED;
            _network = (network != null) ? new NetMatcher(new String[]{network}) : null;
            _hostnamePattern = (hostname != null) ? Pattern.compile(hostname) : null;
        }

        public boolean match(InetAddress remote)
        {
            if (_hostnamePattern != null)
            {
                return _hostnamePattern.matcher(remote.getCanonicalHostName()).matches();
            }
            else
            {
                return _network.matchInetNetwork(remote);
            }
        }

        public AuthzResult getAccess()
        {
            return _access;
        }

    }

    private AuthzResult _default = AuthzResult.ABSTAIN;
    private FirewallRule[] _rules;

    @Override
    public AuthzResult authoriseConnect(AMQProtocolSession session, VirtualHost virtualHost)
    {
        if (!(session instanceof AMQMinaProtocolSession))
        {
            return AuthzResult.ABSTAIN; // We only deal with tcp sessions, which mean MINA right now
        }

        InetAddress addr = getInetAdressFromMinaSession((AMQMinaProtocolSession) session);
        
        if (addr == null)
        {
            return AuthzResult.ABSTAIN; // Not an Inet socket on the other end
        }
        
        boolean match = false;
        for (FirewallRule rule : _rules)
        {
            match = rule.match(addr);
            if (match)
            {
                return rule.getAccess();
            }
        }
        return _default;

    }

    private InetAddress getInetAdressFromMinaSession(AMQMinaProtocolSession session)
    {
        SocketAddress remote = session.getIOSession().getRemoteAddress();
        if (remote instanceof InetSocketAddress)
        {
            return ((InetSocketAddress) remote).getAddress();
        } 
        else
        {
            return null;
        }
    }

    @Override
    public void setConfiguration(Configuration config)
    {
        // Get default action
        String defaultAction = config.getString("[@default-action]");
        if (defaultAction == null) {
            _default = AuthzResult.ABSTAIN;
        } 
        else if (defaultAction.toLowerCase().equals("allow"))
        {
            _default = AuthzResult.ALLOWED;
        }
        else 
        {
            _default = AuthzResult.DENIED;
        }
        
        int numRules = config.getList("rule[@access]").size(); // all rules must
                                                               // have an access
                                                               // attribute
        _rules = new FirewallRule[numRules];
        for (int i = 0; i < numRules; i++)
        {
            FirewallRule rule = new FirewallRule((String) config.getProperty("rule(" + i + ")[@access]"),
                    (String) config.getProperty("rule(" + i + ")[@network]"), (String) config.getProperty("rule(" + i
                            + ")[@hostname]"));
            _rules[i] = rule;
        }
    }
}
