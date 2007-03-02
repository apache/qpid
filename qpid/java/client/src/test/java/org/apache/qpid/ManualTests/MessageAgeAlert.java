package org.apache.qpid.ManualTests;

import junit.framework.TestCase;

import org.apache.qpid.testutil.QpidClientConnection;
import org.apache.qpid.client.transport.TransportConnection;

/** FT401: alert on message age
 *  Provided by customer
 */
public class MessageAgeAlert extends TestCase
{
    protected QpidClientConnection conn;
    protected final String queue = "direct://amq.direct//queue";
    protected final String vhost = "/test";

    protected String payload = "xyzzy";

    protected Integer agePeriod = 30000;

    protected final String BROKER = "localhost";

    protected void log(String msg)
    {
        System.out.println(msg);
    }

    protected void setUp() throws Exception
    {
        super.setUp();

//        TransportConnection.createVMBroker(1);

        conn = new QpidClientConnection(BROKER);
        conn.setVirtualHost(vhost);

        conn.connect();
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        conn.disconnect();
//        TransportConnection.killVMBroker(1);
    }

    /**
     * put a message and wait for age alert
     *
     * @throws Exception on error
     */
    public void testSinglePutThenWait() throws Exception
    {
        conn.put(queue, payload, 1);
        log("waiting ms: " + agePeriod);
        Thread.sleep(agePeriod);
        log("wait period over");
        conn.put(queue, payload, 1);
    }
}
