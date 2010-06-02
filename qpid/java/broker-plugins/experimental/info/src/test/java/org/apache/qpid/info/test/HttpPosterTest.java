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
