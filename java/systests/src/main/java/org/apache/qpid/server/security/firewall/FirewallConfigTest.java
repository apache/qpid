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
package org.apache.qpid.server.security.firewall;

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class FirewallConfigTest extends QpidBrokerTestCase 
{
    private File _tmpConfig, _tmpVirtualhosts;
    private String _ipAddressOfBrokerHost;

    @Override
    protected void setUp() throws Exception
    {
        // Setup initial config file.
        _configFile = new File("build/etc/config-systests-firewall.xml");
        
        // Setup temporary config file
        _tmpConfig = File.createTempFile("config-systests-firewall", ".xml");
        setSystemProperty("QPID_FIREWALL_CONFIG_SETTINGS", _tmpConfig.getAbsolutePath());
        _tmpConfig.deleteOnExit();

        // Setup temporary virtualhosts file
        _tmpVirtualhosts = File.createTempFile("virtualhosts-systests-firewall", ".xml");
        setSystemProperty("QPID_FIREWALL_VIRTUALHOSTS_SETTINGS", _tmpVirtualhosts.getAbsolutePath());
        _tmpVirtualhosts.deleteOnExit();

        _ipAddressOfBrokerHost = getIpAddressOfBrokerHost();
    }

    private void writeFirewallFile(boolean allow, boolean inVhost) throws IOException
    {
        FileWriter out = new FileWriter(inVhost ? _tmpVirtualhosts : _tmpConfig);
        if (inVhost) 
        {
            out.write("<virtualhosts><virtualhost><test>");
        }
        else
        {
            out.write("<broker>");
        }
        out.write("<security><firewall>");
        out.write("<rule access=\""+((allow) ? "allow" : "deny")+"\" network=\"" + _ipAddressOfBrokerHost + "\"/>");
        out.write("</firewall></security>");
        if (inVhost)
        {
            out.write("</test></virtualhost></virtualhosts>");
        }
        else
        {
            out.write("</broker>");
        }
        out.close();
    }

    public void testVhostAllowBrokerDeny() throws Exception
    {

        _configFile = new File("build/etc/config-systests-firewall-2.xml");
        
        super.setUp();
        try 
        {
            //Try to get a connection to the 'test2' vhost
            //This is expected to succeed as it is allowed at the vhost level
            getConnection(new AMQConnectionURL("amqp://guest:guest@clientid/test2?brokerlist='" + getBroker() + "'"));
        } 
        catch (JMSException e)
        {
            e.getLinkedException().printStackTrace();
            fail("The connection was expected to succeed: " + e.getMessage());
        }

        try 
        {
            //Try to get a connection to the 'test' vhost
            //This is expected to fail as it is denied at the broker level
            getConnection();
            fail("We expected the connection to fail");
        } 
        catch (JMSException e)
        {
            //ignore
        }
    }
    
    public void testVhostDenyBrokerAllow() throws Exception
    {
        _configFile = new File("build/etc/config-systests-firewall-3.xml");
        
        super.setUp();
        try 
        {
            //Try to get a connection to the 'test2' vhost
            //This is expected to fail as it is denied at the vhost level
            getConnection(new AMQConnectionURL("amqp://guest:guest@clientid/test2?brokerlist='" + getBroker() + "'"));
            fail("The connection was expected to fail");
        } 
        catch (JMSException e)
        {
            //ignore
        }

        try 
        {
            //Try to get a connection to the 'test' vhost
            //This is expected to succeed as it is allowed at the broker level
            getConnection();
        } 
        catch (JMSException e)
        {
            e.getLinkedException().printStackTrace();
            fail("The connection was expected to succeed: " + e.getMessage());
        }
    }
 
    public void testDenyOnRestart() throws Exception
    {
        testDeny(false, new Runnable() {

            public void run()
            {
                try
                {
                    restartBroker();
                } catch (Exception e)
                {
                    fail(e.getMessage());
                }
            }
        });
    }
    
    public void testDenyOnRestartInVhost() throws Exception
    {
        testDeny(true, new Runnable() {

            public void run()
            {
                try
                {
                    restartBroker();
                } catch (Exception e)
                {
                    fail(e.getMessage());
                }
            }
        });
    }
    
    public void testAllowOnReloadInVhost() throws Exception
    {
        testFirewall(false, true, new Runnable() {

            public void run()
            {
                try
                {
                    reloadBrokerSecurityConfig();
                } catch (Exception e)
                {
                    fail(e.getMessage());
                }
            }
        });
    }
    
    public void testDenyOnReload() throws Exception
    {
        testDeny(false, new Runnable() {

            public void run()
            {
                try
                {
                    reloadBrokerSecurityConfig();
                } catch (Exception e)
                {
                    fail(e.getMessage());
                }
            }
        }
        );
    }

    public void testDenyOnReloadInVhost() throws Exception
    {
        testDeny(true, new Runnable() {

            public void run()
            {
                try
                {
                    reloadBrokerSecurityConfig();
                } catch (Exception e)
                {
                   fail(e.getMessage());
                }
            }
        }
        );
       
    }

    private void testDeny(boolean inVhost, Runnable restartOrReload) throws Exception
    {
        testFirewall(true, inVhost, restartOrReload);
    }

    /*
     * Check we can get a connection
     */
    private boolean checkConnection() throws Exception
    {
        Exception exception  = null;
        Connection conn = null;
        try 
        {
            conn = getConnection();
        } 
        catch (JMSException e)
        {
            exception = e;
        }
        
        return conn != null;
    }

    private void testFirewall(boolean initial, boolean inVhost, Runnable restartOrReload) throws Exception
    {
        
        writeFirewallFile(initial, inVhost);
        setConfigurationProperty("management.enabled", String.valueOf(true));
        super.setUp();

        assertEquals("Initial connection check failed", initial, checkConnection());

        // Reload changed firewall file after restart or reload
        writeFirewallFile(!initial, inVhost);
        restartOrReload.run();
        
        assertEquals("Second connection check failed", !initial, checkConnection());
    }

    private String getIpAddressOfBrokerHost()
    {
        String brokerHost = getBroker().getHost();
        try
        {
            return InetAddress.getByName(brokerHost).getHostAddress();
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException("Could not determine IP address of host : " + brokerHost, e);
        }

    }
}
