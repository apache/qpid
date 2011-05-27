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

    private ServletTester tester;

    private String baseURL;

    private final String contextPath = "/info";

    /*
     * This method generates a dummy HttpPoster with a dummy body containing a
     * single line. The url we are posting to can be controlled by the parameter
     * url
     * 
     * @param url
     */
    private HttpPoster getHttpPoster(String url)
    {
        StringBuffer sb = new StringBuffer("test=TEST");
        Properties props = new Properties();
        props.put("http.url", url);
        return new HttpPoster(props, sb);
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        tester = new ServletTester();
        tester.setContextPath("/");
        tester.addServlet(InfoServlet.class, contextPath);
        baseURL = tester.createSocketConnector(true);
        tester.start();
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception
    {
        super.tearDown();
        tester.stop();
    }

    /*
     * This test is posting a string to an embedded Jetty Servlet and captures
     * the response message. If the servlet receives the message ok, it will
     * print Ok. A failure test is following where we post to a non-existent URL
     */
    public void testHttpPoster() throws Exception
    {
        // Test HttpPoster posts correctly to the servlet
        HttpPoster hp = getHttpPoster(baseURL + contextPath);
        assertNotNull(hp);
        hp.run();
        List<String> response = hp.get_response();
        assertTrue(response.size() > 0);
        assertEquals("OK <br>", response.get(0).toString());

        // Failure Test
        hp = getHttpPoster("http://localhost/nonexistent");
        hp.run();
        response = hp.get_response();
        assertTrue(response.size() == 0);

    }

}
