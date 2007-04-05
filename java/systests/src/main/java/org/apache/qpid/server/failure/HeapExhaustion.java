package org.apache.qpid.server.failure;

import junit.framework.TestCase;
import org.apache.qpid.testutil.QpidClientConnection;


/** Test Case provided by client Non-functional Test NF101: heap exhaustion behaviour */
public class HeapExhaustion extends TestCase
{
    protected QpidClientConnection conn;
    protected final String BROKER = "localhost";
    protected final String vhost = "/test";
    protected final String queue = "direct://amq.direct//queue";

    protected String hundredK;
    protected String megabyte;

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
        conn.disconnect();
    }


    /** PUT at maximum rate (although we commit after each PUT) until failure
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
            log("put copy " + copies + " OK for total bytes: " + total);
        }
    }

    /** PUT at lower rate (5 per second) until failure
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
            log("put copy " + copies + " OK for total bytes: " + total);
            Thread.sleep(200);
        }
    }
}
