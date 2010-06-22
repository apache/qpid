package org.apache.qpid.info;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class InfoPluginTest extends QpidBrokerTestCase
{
    private String QPID_HOME = null;

    private ServerSocket server = null;

    private final int port = 9000;

    private static final String CR = System.getProperty("line.separator");

    private static final String FS = File.separator;

    private final String cfgRelPath = "etc" + FS + "qpidinfo.properties";

    private File tmpCfgFile;

    private final String soapEnvelopeHead = "<?xml version=\"1.0\"?><soap:Envelope xmlns:soap=\"http://www.w3.org/2001/12/soap-envelope\" soap:encodingStyle=\"http://www.w3.org/2001/12/soap-encoding\">";

    private final String soapEnvelopeTail = "</soap:Envelope>";

    private String soapMessage1 = "@ACTION" + "-" + "@VERSION";

    private String soapMessage2 = "@VERSION" + "-" + "@ACTION";

    private CountDownLatch latch = new CountDownLatch(2);

    final List<List<String>> recv = new ArrayList<List<String>>();

    Thread socketAcceptor;

    public void setUp() throws Exception
    {
        QPID_HOME = System.getProperty("QPID_HOME");
        if (QPID_HOME != null)
        {
            System.out.println("QPID_HOME=" + QPID_HOME);
        } else
        {
            fail("QPID_HOME not set");
        }
        createConfigFile();
        startSoapServer(port);
    }

    public void tearDown() throws Exception
    {
        System.out.println("*** Deleting the config file...");
        if (tmpCfgFile.isFile())
            tmpCfgFile.delete();
        super.tearDown();
    }

    private void createConfigFile()
    {
        try
        {
            tmpCfgFile = new File(QPID_HOME + FS + cfgRelPath);
            if (tmpCfgFile.isFile())
                tmpCfgFile.delete();
            tmpCfgFile.createNewFile();
            assertTrue(tmpCfgFile.isFile());
            FileWriter fwriter = new FileWriter(tmpCfgFile);
            BufferedWriter writer = new BufferedWriter(fwriter);
            writer.write("protocol=soap");
            writer.write(CR);
            writer.write("soap.hostname=localhost");
            writer.write(CR);
            writer.write("soap.port=" + port);
            writer.write(CR);
            writer.write(CR);
            writer.write("[MSG1]");
            writer.write(CR);
            writer.write("soap.path=/info1");
            writer.write(CR);
            writer.write("soap.action=submitinfo1");
            writer.write(CR);
            writer.write("soap.envelope=" + soapEnvelopeHead + soapMessage1
                    + soapEnvelopeTail);
            writer.write(CR);
            writer.write(CR);
            writer.write("[MSG2]");
            writer.write(CR);
            writer.write("soap.path=/info2");
            writer.write(CR);
            writer.write("soap.action=submitinfo2");
            writer.write(CR);
            writer.write("soap.envelope=" + soapEnvelopeHead + soapMessage2
                    + soapEnvelopeTail);
            writer.write(CR);
            writer.write(CR);
            writer.close();
            assertTrue("Config file size is zero", tmpCfgFile.length() > 0);
        } catch (IOException e)
        {
            fail("Unable to create the qpidinfo.properties due to: "
                    + e.getMessage());
        }
    }

    private void startSoapServer(int port) throws Exception
    {
        assertTrue("Port a negative number", port > 0);
        assertTrue("Port higher than 65535", port < 65535);

        try
        {
            server = new ServerSocket(port);
            assertNotNull("SocketServer is null", server);
        } catch (Exception ex)
        {
            fail("Unable to start the socket server due to: " + ex.getMessage());
        }

        socketAcceptor = new Thread()
        {
            public void run()
            {
                while (true)
                {
                    try
                    {
                        Socket socket = server.accept();
                        new ConnectionHandler(socket);
                    } catch (IOException e)
                    {
                        fail("Error opening the socket in accept mode");
                    }
                }
            }
        };
        socketAcceptor.start();
        System.out.println("*** Socket server started...");
    }

    class ConnectionHandler implements Runnable
    {
        private Socket socket;

        public ConnectionHandler(Socket socket)
        {
            this.socket = socket;
            Thread t = new Thread(this);
            t.start();
        }

        public void run()
        {
            System.out.println("*** Connection handler running...");
            List<String> buf = new ArrayList<String>();
            String line;
            try
            {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                assertNotNull(br);
                while ((line = br.readLine()) != null)
                {
                    buf.add(line);
                }
                br.close();
                System.out.println("*** Received buffer: " + buf);
                System.out.println("*** Latch countdown");
                latch.countDown();
                synchronized (recv)
                {
                    recv.add(buf);
                }
            } catch (Exception ex)
            {
                ex.printStackTrace();
                fail("Exception while reading from the socket");
            }

        }

    }

    public void testInfoPlugin() throws Exception
    {
        super.setUp();
        if (!latch.await(10, TimeUnit.SECONDS))
        {
            fail("Timeout awaiting for the latch, upon startup");
        }

        assertTrue("Received less than 2 messages", recv.size() > 1);
        //Message 1
        assertTrue("Message has 0 size", recv.get(0).size()>0);
        assertEquals("Message does not have 8 fields", recv.get(0).size(), 8);
        assertTrue("Message does not contain Host: localhost:9000",recv.get(0).contains("Host: localhost:9000"));
        assertTrue("Message does not contain: User-Agent: Axis2 ", recv.get(0).contains("User-Agent: Axis2"));
        assertTrue("Message does not contain: SOAPAction: \"urn:submitinfo\"", recv.get(0).get(4).startsWith("SOAPAction: \"urn:submitinfo"));
        assertTrue("Message does not contain STARTUP in the soap envelope", recv.get(0).get(7).contains("STARTUP"));
        
        //Message 2
        assertTrue("Message has 0 size", recv.get(1).size()>0);
        assertEquals("Message does not have 8 fields", recv.get(1).size(), 8);
        assertTrue("Message does not contain Host: localhost:9000",recv.get(1).contains("Host: localhost:9000"));
        assertTrue("Message does not contain: User-Agent: Axis2 ", recv.get(1).contains("User-Agent: Axis2"));
        assertTrue("Message does not contain: SOAPAction: \"urn:submitinfo\"", recv.get(1).get(4).startsWith("SOAPAction: \"urn:submitinfo"));
        assertTrue("Message does not contain STARTUP in the soap envelope", recv.get(0).get(7).contains("STARTUP"));
        
        recv.clear();
        latch = new CountDownLatch(2);
   
        stopBroker();
        
        if (!latch.await(10, TimeUnit.SECONDS))
        {
            fail("Timeout awaiting for the latch, upon shutdown");
        }
        
        
        assertTrue("Received less than 2 messages", recv.size() > 1);
        
         // Message 1
        assertTrue("Message does not contain Host: localhost:9000",recv.get(0).contains("Host: localhost:9000"));
        assertTrue("Message does not contain: User-Agent: Axis2 ", recv.get(0).contains("User-Agent: Axis2"));
        assertTrue("Message does not contain: SOAPAction: \"urn:submitinfo\"", recv.get(0).get(4).startsWith("SOAPAction: \"urn:submitinfo"));
        assertTrue("Message does not contain SHUTDOWN in the soap envelope", recv.get(0).get(7).contains("SHUTDOWN"));
        
        // Message 2
        assertTrue("Message does not contain Host: localhost:9000",recv.get(1).contains("Host: localhost:9000"));
        assertTrue("Message does not contain: User-Agent: Axis2 ", recv.get(1).contains("User-Agent: Axis2"));
        assertTrue("Message does not contain: SOAPAction: \"urn:submitinfo\"", recv.get(1).get(4).startsWith("SOAPAction: \"urn:submitinfo"));
        assertTrue("Message does not contain SHUTDOWN in the soap envelope", recv.get(0).get(7).contains("SHUTDOWN"));
        
        System.out.println("*** Stopping socket server...");
        socketAcceptor.join(2000);
    }

}
