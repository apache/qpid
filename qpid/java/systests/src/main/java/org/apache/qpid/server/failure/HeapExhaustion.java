package org.apache.qpid.server.failure;

import junit.framework.TestCase;
import org.apache.qpid.testutil.QpidClientConnection;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.log4j.Logger;

import javax.jms.JMSException;
import java.io.IOException;


/** Test Case provided by client Non-functional Test NF101: heap exhaustion behaviour */
public class HeapExhaustion extends TestCase
{
    private static final Logger _logger = Logger.getLogger(HeapExhaustion.class);

    protected QpidClientConnection conn;                         
    protected final String BROKER = "localhost";
    protected final String vhost = "/test";
    protected final String queue = "direct://amq.direct//queue";

    protected String hundredK;
    protected String megabyte;

    protected String generatePayloadOfSize(Integer numBytes)
    {
        return new String(new byte[numBytes]);
    }

    protected void setUp() throws Exception
    {
        conn = new QpidClientConnection(BROKER);
        conn.setVirtualHost(vhost);

        conn.connect();
        // clear queue
        _logger.debug("setup: clearing test queue");
        conn.consume(queue, 2000);

        hundredK = generatePayloadOfSize(1024 * 100);
        megabyte = generatePayloadOfSize(1024 * 1024);
    }

    protected void tearDown() throws Exception
    {
        conn.disconnect();
    }


    /**
     * PUT at maximum rate (although we commit after each PUT) until failure
     *
     * @throws Exception on error
     */
    public void testUntilFailure() throws Exception
    {
        int copies = 0;
        int total = 0;
        String payload = hundredK;
        int size = payload.getBytes().length;
        while (true)
        {
            conn.put(queue, payload, 1);
            copies++;
            total += size;
            System.out.println("put copy " + copies + " OK for total bytes: " + total);
        }
    }

    /**
     * PUT at lower rate (5 per second) until failure
     *
     * @throws Exception on error
     */
    public void testUntilFailureWithDelays() throws Exception
    {
        int copies = 0;
        int total = 0;
        String payload = hundredK;
        int size = payload.getBytes().length;
        while (true)
        {
            conn.put(queue, payload, 1);
            copies++;
            total += size;
            System.out.println("put copy " + copies + " OK for total bytes: " + total);
            Thread.sleep(200);
        }
    }

    public static void noDelay()
    {
        HeapExhaustion he = new HeapExhaustion();

        try
        {
            he.setUp();
        }
        catch (Exception e)
        {
            _logger.info("Unable to connect");
            System.exit(0);
        }

        try
        {
            _logger.info("Running testUntilFailure");
            try
            {
                he.testUntilFailure();
            }
            catch (FailoverException fe)
            {
                _logger.error("Caught failover:" + fe);
            }
            _logger.info("Finishing Connection ");

            try
            {
                he.tearDown();
            }
            catch (JMSException jmse)
            {
                if (((AMQException) jmse.getLinkedException()).getErrorCode() == AMQConstant.REQUEST_TIMEOUT)
                {
                    _logger.info("Successful test of testUntilFailure");
                }
                else
                {
                    _logger.error("Test Failed due to:" + jmse);
                }
            }
        }
        catch (Exception e)
        {
            _logger.error("Test Failed due to:" + e);
        }
    }

    public static void withDelay()
    {
        HeapExhaustion he = new HeapExhaustion();

        try
        {
            he.setUp();
        }
        catch (Exception e)
        {
            _logger.info("Unable to connect");
            System.exit(0);
        }

        try
        {
            _logger.info("Running testUntilFailure");
            try
            {
                he.testUntilFailureWithDelays();
            }
            catch (FailoverException fe)
            {
                _logger.error("Caught failover:" + fe);
            }
            _logger.info("Finishing Connection ");

            try
            {
                he.tearDown();
            }
            catch (JMSException jmse)
            {
                if (((AMQException) jmse.getLinkedException()).getErrorCode() == AMQConstant.REQUEST_TIMEOUT)
                {
                    _logger.info("Successful test of testUntilFailure");
                }
                else
                {
                    _logger.error("Test Failed due to:" + jmse);
                }
            }
        }
        catch (Exception e)
        {
            _logger.error("Test Failed due to:" + e);
        }
    }

    public static void main(String args[])
    {
        noDelay();


        try
        {
            System.out.println("Restart failed broker now to retest broker with delays in send.");
            System.in.read();
        }
        catch (IOException e)
        {
            _logger.info("Continuing");
        }

        withDelay();
    }
}
