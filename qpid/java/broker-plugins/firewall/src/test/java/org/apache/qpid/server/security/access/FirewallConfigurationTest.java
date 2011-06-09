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
package org.apache.qpid.server.security.access;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class FirewallConfigurationTest extends InternalBrokerBaseCase
{
    public void testFirewallConfiguration() throws Exception
    {
        // Write out config
        File mainFile = File.createTempFile(getClass().getName(), null);
        mainFile.deleteOnExit();
        writeConfigFile(mainFile, false);

        // Load config
        ApplicationRegistry reg = new ConfigurationFileApplicationRegistry(mainFile);
        try
        {
            ApplicationRegistry.initialise(reg, 1);

            // Test config
            assertFalse(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));
            assertTrue(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.1.2.3", 65535)));
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }
    }

    public void testCombinedConfigurationFirewall() throws Exception
    {
        // Write out config
        File mainFile = File.createTempFile(getClass().getName(), null);
        File fileA = File.createTempFile(getClass().getName(), null);
        File fileB = File.createTempFile(getClass().getName(), null);

        mainFile.deleteOnExit();
        fileA.deleteOnExit();
        fileB.deleteOnExit();

        FileWriter out = new FileWriter(mainFile);
        out.write("<configuration><system/>");
        out.write("<xml fileName=\"" + fileA.getAbsolutePath() + "\"/>");
        out.write("</configuration>");
        out.close();

        out = new FileWriter(fileA);
        out.write("<broker>\n");
        out.write("\t<plugin-directory>${QPID_HOME}/lib/plugins</plugin-directory>\n");
        out.write("\t<cache-directory>${QPID_WORK}/cache</cache-directory>\n");
        out.write("\t<management><enabled>false</enabled></management>\n");
        out.write("\t<security>\n");
        out.write("\t\t<principal-databases>\n");
        out.write("\t\t\t<principal-database>\n");
        out.write("\t\t\t\t<name>passwordfile</name>\n");
        out.write("\t\t\t\t<class>org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase</class>\n");
        out.write("\t\t\t\t<attributes>\n");
        out.write("\t\t\t\t\t<attribute>\n");
        out.write("\t\t\t\t\t\t<name>passwordFile</name>\n");
        out.write("\t\t\t\t\t\t<value>/dev/null</value>\n");
        out.write("\t\t\t\t\t</attribute>\n");
        out.write("\t\t\t\t</attributes>\n");
        out.write("\t\t\t</principal-database>\n");
        out.write("\t\t</principal-databases>\n");
        out.write("\t\t<firewall>\n");
        out.write("\t\t\t<xml fileName=\"" + fileB.getAbsolutePath() + "\"/>");
        out.write("\t\t</firewall>\n");
        out.write("\t</security>\n");
        out.write("\t<virtualhosts>\n");
        out.write("\t\t<virtualhost>\n");
        out.write("\t\t\t<name>test</name>\n");
        out.write("\t\t</virtualhost>\n");
        out.write("\t</virtualhosts>\n");
        out.write("</broker>\n");
        out.close();

        out = new FileWriter(fileB);
        out.write("<firewall>\n");
        out.write("\t<rule access=\"deny\" network=\"127.0.0.1\"/>");
        out.write("</firewall>\n");
        out.close();

        // Load config
        ApplicationRegistry reg = new ConfigurationFileApplicationRegistry(mainFile);
        try
        {
            ApplicationRegistry.initialise(reg, 1);

            // Test config
            assertFalse(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }
    }

    public void testConfigurationFirewallReload() throws Exception
    {
        // Write out config
        File mainFile = File.createTempFile(getClass().getName(), null);

        mainFile.deleteOnExit();
        writeConfigFile(mainFile, false);

        // Load config
        ApplicationRegistry reg = new ConfigurationFileApplicationRegistry(mainFile);
        try
        {
            ApplicationRegistry.initialise(reg, 1);

            // Test config
            assertFalse(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));

            // Switch to deny the connection
            writeConfigFile(mainFile, true);

            reg.getConfiguration().reparseConfigFileSecuritySections();

            assertTrue(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }
    }

    public void testCombinedConfigurationFirewallReload() throws Exception
    {
        // Write out config
        File mainFile = File.createTempFile(getClass().getName(), null);
        File fileA = File.createTempFile(getClass().getName(), null);
        File fileB = File.createTempFile(getClass().getName(), null);

        mainFile.deleteOnExit();
        fileA.deleteOnExit();
        fileB.deleteOnExit();

        FileWriter out = new FileWriter(mainFile);
        out.write("<configuration><system/>");
        out.write("<xml fileName=\"" + fileA.getAbsolutePath() + "\"/>");
        out.write("</configuration>");
        out.close();

        out = new FileWriter(fileA);
        out.write("<broker>\n");
        out.write("\t<plugin-directory>${QPID_HOME}/lib/plugins</plugin-directory>\n");
        out.write("\t<management><enabled>false</enabled></management>\n");
        out.write("\t<security>\n");
        out.write("\t\t<principal-databases>\n");
        out.write("\t\t\t<principal-database>\n");
        out.write("\t\t\t\t<name>passwordfile</name>\n");
        out.write("\t\t\t\t<class>org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase</class>\n");
        out.write("\t\t\t\t<attributes>\n");
        out.write("\t\t\t\t\t<attribute>\n");
        out.write("\t\t\t\t\t\t<name>passwordFile</name>\n");
        out.write("\t\t\t\t\t\t<value>/dev/null</value>\n");
        out.write("\t\t\t\t\t</attribute>\n");
        out.write("\t\t\t\t</attributes>\n");
        out.write("\t\t\t</principal-database>\n");
        out.write("\t\t</principal-databases>\n");
        out.write("\t\t<firewall>\n");
        out.write("\t\t\t<xml fileName=\"" + fileB.getAbsolutePath() + "\"/>");
        out.write("\t\t</firewall>\n");
        out.write("\t</security>\n");
        out.write("\t<virtualhosts>\n");
        out.write("\t\t<virtualhost>\n");
        out.write("\t\t\t<name>test</name>\n");
        out.write("\t\t</virtualhost>\n");
        out.write("\t</virtualhosts>\n");
        out.write("</broker>\n");
        out.close();

        out = new FileWriter(fileB);
        out.write("<firewall>\n");
        out.write("\t<rule access=\"deny\" network=\"127.0.0.1\"/>");
        out.write("</firewall>\n");
        out.close();

        // Load config
        ApplicationRegistry reg = new ConfigurationFileApplicationRegistry(mainFile);
        try
        {
            ApplicationRegistry.initialise(reg, 1);

            // Test config
            assertFalse(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));

            RandomAccessFile fileBRandom = new RandomAccessFile(fileB, "rw");
            fileBRandom.setLength(0);
            fileBRandom.seek(0);
            fileBRandom.close();

            out = new FileWriter(fileB);
            out.write("<firewall>\n");
            out.write("\t<rule access=\"allow\" network=\"127.0.0.1\"/>");
            out.write("</firewall>\n");
            out.close();

            reg.getConfiguration().reparseConfigFileSecuritySections();

            assertTrue(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));

            fileBRandom = new RandomAccessFile(fileB, "rw");
            fileBRandom.setLength(0);
            fileBRandom.seek(0);
            fileBRandom.close();

            out = new FileWriter(fileB);
            out.write("<firewall>\n");
            out.write("\t<rule access=\"deny\" network=\"127.0.0.1\"/>");
            out.write("</firewall>\n");
            out.close();

            reg.getConfiguration().reparseConfigFileSecuritySections();

            assertFalse(reg.getSecurityManager().accessVirtualhost("test", new InetSocketAddress("127.0.0.1", 65535)));
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }
    }

    private void writeFirewallVhostsFile(File vhostsFile, boolean allow) throws IOException
    {
        FileWriter out = new FileWriter(vhostsFile);
        String ipAddr = "127.0.0.1"; // FIXME: get this from InetAddress.getLocalHost().getAddress() ?
        out.write("<virtualhosts><virtualhost>");
        out.write("<name>test</name>");
        out.write("<test>");
        out.write("<security><firewall>");
        out.write("<rule access=\""+((allow) ? "allow" : "deny")+"\" network=\""+ipAddr +"\"/>");
        out.write("</firewall></security>");
        out.write("</test>");
        out.write("</virtualhost></virtualhosts>");
        out.close();
    }

    private void writeConfigFile(File mainFile, boolean allow) throws IOException {
        writeConfigFile(mainFile, allow, true, null, "test");
    }

    /*
        XMLConfiguration config = new XMLConfiguration(mainFile);
        PluginManager pluginManager = new MockPluginManager("");
        SecurityManager manager = new SecurityManager(config, pluginManager, Firewall.FACTORY);
        
     */
    private void writeConfigFile(File mainFile, boolean allow, boolean includeVhosts, File vhostsFile, String name) throws IOException {
        FileWriter out = new FileWriter(mainFile);
        out.write("<broker>\n");
        out.write("\t<plugin-directory>${QPID_HOME}/lib/plugins</plugin-directory>\n");
        out.write("\t<management><enabled>false</enabled></management>\n");
        out.write("\t<security>\n");
        out.write("\t\t<principal-databases>\n");
        out.write("\t\t\t<principal-database>\n");
        out.write("\t\t\t\t<name>passwordfile</name>\n");
        out.write("\t\t\t\t<class>org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase</class>\n");
        out.write("\t\t\t\t<attributes>\n");
        out.write("\t\t\t\t\t<attribute>\n");
        out.write("\t\t\t\t\t\t<name>passwordFile</name>\n");
        out.write("\t\t\t\t\t\t<value>/dev/null</value>\n");
        out.write("\t\t\t\t\t</attribute>\n");
        out.write("\t\t\t\t</attributes>\n");
        out.write("\t\t\t</principal-database>\n");
        out.write("\t\t</principal-databases>\n");
        out.write("\t\t<firewall>\n");
        out.write("\t\t\t<rule access=\""+ ((allow) ? "allow" : "deny") +"\" network=\"127.0.0.1\"/>");
        out.write("\t\t</firewall>\n");
        out.write("\t</security>\n");
        if (includeVhosts)
        {
	        out.write("\t<virtualhosts>\n");
            out.write("\t\t<default>test</default>\n");
	        out.write("\t\t<virtualhost>\n");
	        out.write(String.format("\t\t\t<name>%s</name>\n", name));
	        out.write("\t\t</virtualhost>\n");
	        out.write("\t</virtualhosts>\n");
        }
        if (vhostsFile != null)
        {
        	out.write("\t<virtualhosts>"+vhostsFile.getAbsolutePath()+"</virtualhosts>\n");	
        }
        out.write("</broker>\n");
        out.close();
    }
    
    /**
     * Test that configuration loads correctly when virtual hosts are specified in an external
     * configuration file only.
     * <p>
     * Test for QPID-2360
     */
    public void testExternalFirewallVirtualhostXMLFile() throws Exception
    {
        // Write out config
        File mainFile = File.createTempFile(getClass().getName(), "config");
        mainFile.deleteOnExit();
        File vhostsFile = File.createTempFile(getClass().getName(), "vhosts");
        vhostsFile.deleteOnExit();
        writeConfigFile(mainFile, false, false, vhostsFile, null);    
        writeFirewallVhostsFile(vhostsFile, false);

        // Load config
        ApplicationRegistry reg = new ConfigurationFileApplicationRegistry(mainFile);
        ApplicationRegistry.initialise(reg, 1);

        // Test config
        VirtualHostRegistry virtualHostRegistry = reg.getVirtualHostRegistry();
        VirtualHost virtualHost = virtualHostRegistry.getVirtualHost("test");

        assertEquals("Incorrect virtualhost count", 1, virtualHostRegistry.getVirtualHosts().size());
        assertEquals("Incorrect virtualhost name", "test", virtualHost.getName());
    }
}
