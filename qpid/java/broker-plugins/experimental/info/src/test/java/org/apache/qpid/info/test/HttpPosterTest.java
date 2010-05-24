package org.apache.qpid.info.test;

import java.util.List;
import java.util.Properties;

import org.apache.qpid.info.util.HttpPoster;
import org.mortbay.jetty.testing.ServletTester;

import junit.framework.TestCase;

/*
 * This test verifies that the plugin posts correctly to a webserver 
 * We use an embedded jetty container to mimic the webserver
 */
public class HttpPosterTest extends TestCase
{

    private HttpPoster hp;

    private Properties props;

    private StringBuffer sb;

    private ServletTester tester;

    private String baseURL;

    private final String contextPath = "/info";

    protected void setUp() throws Exception
    {
        super.setUp();

        tester = new ServletTester();
        tester.setContextPath("/");
        tester.addServlet(InfoServlet.class, contextPath);
        baseURL = tester.createSocketConnector(true);
        tester.start();
        //       
        props = new Properties();
        props.put("URL", baseURL + contextPath);
        props.put("hostname", "localhost");
        sb = new StringBuffer("test=TEST");
        hp = new HttpPoster(props, sb);

    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        hp = null;
        props = null;
        sb = null;
        tester.stop();
    }

    public void testHttpPoster()
    {
        assertNotNull(hp);
        hp.run();
        List<String> response = hp.getResponse();
        assertTrue(response.size() > 0);
        assertEquals("OK <br>", response.get(0).toString());
    }

}
