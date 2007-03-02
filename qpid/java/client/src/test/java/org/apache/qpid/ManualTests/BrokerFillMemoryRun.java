package org.apache.qpid.ManualTests;

import junit.framework.TestCase;
import org.apache.qpid.testutil.QpidClientConnection;

import javax.jms.JMSException;

/** NF101: heap exhaustion behaviour
 *  Provided by customer 
 */
public class BrokerFillMemoryRun extends TestCase
{
    protected QpidClientConnection conn;
    protected final String vhost = "/test";
    protected final String queue = "direct://amq.direct//queue";

    protected String hundredK;
    protected String megabyte;

    protected final String BROKER = "tcp://localhost:5672";


    protected void log(String msg)
    {
        System.out.println(msg);
    }

    protected String generatePayloadOfSize(Integer numBytes)
    {
        return new String(new byte[numBytes]);
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        conn = new QpidClientConnection(BROKER);
        conn.setVirtualHost(vhost);

        conn.connect();
        // clear queue
        log("setup: clearing test queue");
        conn.consume(queue, 2000);

        hundredK = generatePayloadOfSize(1024 * 100);
        megabyte = generatePayloadOfSize(1024 * 1024);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        try
        {
            conn.disconnect();
        }
        catch (JMSException d)
        {
            log("disconnectJMSE:" + d.getMessage());
        }
    }


    /** PUT at maximum rate (although we commit after each PUT) until failure */
    public void testUntilFailure() throws Exception
    {
        int copies = 0;
        int total = 0;
        String payload = hundredK;
        int size = payload.getBytes().length + String.valueOf("0").getBytes().length;
        while (true)
        {
            conn.put(queue, payload, 1);
            copies++;
            total += size;
            log("put copy " + copies + " OK for total bytes: " + total);
        }
    }

    /** PUT at lower rate (5 per second) until failure */
    public void testUntilFailureWithDelays() throws Exception
    {
        try
        {
            int copies = 0;
            int total = 0;
            String payload = hundredK;
            int size = payload.getBytes().length + String.valueOf("0").getBytes().length;
            while (true)
            {
                conn.put(queue, payload, 1);
                copies++;
                total += size;
                log("put copy " + copies + " OK for total bytes: " + total);
                Thread.sleep(200);
            }
        }
        catch (JMSException e)
        {
            log("putJMSE:" + e.getMessage());
        }
    }
}
