package org.apache.qpid.server.security.firewall;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.qpid.test.utils.QpidTestCase;

public class FirewallConfigTest extends QpidTestCase 
{

    private File tmpFile = null;
    @Override
    protected void setUp() throws Exception
    {
        // do setup
        final String QPID_HOME = System.getProperty("QPID_HOME");

        if (QPID_HOME == null)
        {
            fail("QPID_HOME not set");
        }

        // Setup initial config.
        _configFile = new File(QPID_HOME, "etc/config-systests-firewall.xml");
        tmpFile = File.createTempFile("config-systests-firewall", ".xml");
        setSystemProperty("QPID_FIREWALL_SETTINGS", tmpFile.getAbsolutePath());
        tmpFile.deleteOnExit();
    }

    private void writeFirewallFile(boolean allow, boolean inVhost) throws IOException
    {
        FileWriter out = new FileWriter(tmpFile);
        String ipAddr = "127.0.0.1"; // FIXME: get this from InetAddress.getLocalHost().getAddress() ?
        out.write("<broker>");
        if (inVhost) 
        {
            out.write("<virtualhosts><virtualhost><test>");
        }
        out.write("<security><firewall>");
        out.write("<rule access=\""+((allow) ? "allow" : "deny")+"\" network=\""+ipAddr +"\"/>");
        out.write("</firewall></security>");
        if (inVhost)
        {
            out.write("</test></virtualhost></virtualhosts>");
        }
        out.write("</broker>");
        out.close();
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
                    reloadBroker();
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
                    reloadBroker();
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
                    reloadBroker();
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
        if (_broker.equals(VM))
        {
            // No point running this test in a vm broker
            return;
        }
        
        writeFirewallFile(false, inVhost);        
        super.setUp();
        
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
        assertNotNull(exception);
        
        // Check we can get a connection

        writeFirewallFile(true, inVhost);
        restartOrReload.run();
        
        exception = null;
        try 
        {
            conn = getConnection();
        } 
        catch (JMSException e)
        {
            exception = e;
        }
        assertNull(exception);
    }    
}
