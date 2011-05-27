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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class FirewallConfigTest extends QpidBrokerTestCase 
{
    private File _tmpConfig, _tmpVirtualhosts;
    
    @Override
    protected void setUp() throws Exception
    {
        // do setup
        final String QPID_HOME = System.getProperty("QPID_HOME");

        if (QPID_HOME == null)
        {
            fail("QPID_HOME not set");
        }

        // Setup initial config file.
        _configFile = new File(QPID_HOME, "etc/config-systests-firewall.xml");
        
        // Setup temporary config file
        _tmpConfig = File.createTempFile("config-systests-firewall", ".xml");
        setSystemProperty("QPID_FIREWALL_CONFIG_SETTINGS", _tmpConfig.getAbsolutePath());
        _tmpConfig.deleteOnExit();

        // Setup temporary virtualhosts file
        _tmpVirtualhosts = File.createTempFile("virtualhosts-systests-firewall", ".xml");
        setSystemProperty("QPID_FIREWALL_VIRTUALHOSTS_SETTINGS", _tmpVirtualhosts.getAbsolutePath());
        _tmpVirtualhosts.deleteOnExit();
    }

    private void writeFirewallFile(boolean allow, boolean inVhost) throws IOException
    {
        FileWriter out = new FileWriter(inVhost ? _tmpVirtualhosts : _tmpConfig);
        String ipAddr = "127.0.0.1"; // FIXME: get this from InetAddress.getLocalHost().getAddress() ?
        if (inVhost) 
        {
            out.write("<virtualhosts><virtualhost><test>");
        }
        else
        {
            out.write("<broker>");
        }
        out.write("<security><firewall>");
        out.write("<rule access=\""+((allow) ? "allow" : "deny")+"\" network=\""+ipAddr +"\"/>");
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
        if (_broker.equals(VM))
        {
            //No point running this test with an InVM broker as the
            //firewall plugin only functions for TCP connections.
            return;
        }

        _configFile = new File(System.getProperty("QPID_HOME"), "etc/config-systests-firewall-2.xml");
        
        super.setUp();
        
        Connection conn = null;
        try 
        {
            //Try to get a connection to the 'test2' vhost
            //This is expected to succeed as it is allowed at the vhost level
            conn = getConnection(new AMQConnectionURL("amqp://guest:guest@clientid/test2?brokerlist='" + getBroker() + "'"));
        } 
        catch (JMSException e)
        {
            e.getLinkedException().printStackTrace();
            fail("The connection was expected to succeed: " + e.getMessage());
        }
        
        conn = null;
        try 
        {
            //Try to get a connection to the 'test' vhost
            //This is expected to fail as it is denied at the broker level
            conn = getConnection();
            fail("We expected the connection to fail");
        } 
        catch (JMSException e)
        {
            //ignore
        }
    }
    
    public void testVhostDenyBrokerAllow() throws Exception
    {
        if (_broker.equals(VM))
        {
            //No point running this test with an InVM broker as the
            //firewall plugin only functions for TCP connections.
            return;
        }
        
        _configFile = new File(System.getProperty("QPID_HOME"), "etc/config-systests-firewall-3.xml");
        
        super.setUp();
        
        Connection conn = null;
        try 
        {
            //Try to get a connection to the 'test2' vhost
            //This is expected to fail as it is denied at the vhost level
            conn = getConnection(new AMQConnectionURL("amqp://guest:guest@clientid/test2?brokerlist='" + getBroker() + "'"));
            fail("The connection was expected to fail");
        } 
        catch (JMSException e)
        {
            //ignore
        }

        conn = null;
        try 
        {
            //Try to get a connection to the 'test' vhost
            //This is expected to succeed as it is allowed at the broker level
            conn = getConnection();
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
        if (_broker.equals(VM))
        {
            // No point running this test in a vm broker
            return;
        }
        
        writeFirewallFile(initial, inVhost);
        setConfigurationProperty("management.enabled", String.valueOf(true));
        super.setUp();

        assertEquals("Initial connection check failed", initial, checkConnection());

        // Reload changed firewall file after restart or reload
        writeFirewallFile(!initial, inVhost);
        restartOrReload.run();
        
        assertEquals("Second connection check failed", !initial, checkConnection());
    }
}
