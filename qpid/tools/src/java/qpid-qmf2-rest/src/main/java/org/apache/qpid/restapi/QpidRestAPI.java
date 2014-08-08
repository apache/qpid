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
package org.apache.qpid.restapi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import org.apache.qpid.qmf2.util.GetOpt;

import org.apache.qpid.restapi.httpserver.Authenticator;
import org.apache.qpid.restapi.httpserver.Delegator;

/**
 * Note QpidRestAPI makes use of the Java 1.6 "Easter Egg" HttpServer and associated classes to create a simple
 * HTTP REST interface to Qpid and QMF2 functionality.
 * <p>
 * Because HttpServer is in the com.sun.net.httpserver.HttpServer namespace it is technically not part of core Java
 * and so may not be supported in all JREs, however it seems to be present in the OpenJDK Runtime Environment that is 
 * installed on many Linux variants. It is documented here <a href="http://docs.oracle.com/javase/6/docs/jre/api/net/httpserver/spec/overview-summary.html">com.sun.net.httpserver</a>
 * <p>
 * The reason for choosing com.sun.net.httpserver.HttpServer is simply in order to provide a very lightweight and
 * dependency free approach to creating an HTTP Proxy. Clearly other approaches such as full-blown Servlet containers
 * and the like might provide a better mechanism for some environments, but HttpServer really is very simple to use.
 * <p>
 * If there is a desire to use Servlets rather than HttpServer it should be fairly straightforward as two interfaces
 * have been provided (Server and HttpTransaction) that abstract the key behaviour, so for example a concrete HttpTransaction 
 * implementation could be created by wrapping a com.sun.net.httpserver.HttpExchange, but equally another implementation 
 * could wrap javax.servlet.http.HttpServletRequest and javax.servlet.http.HttpServletResponse, so for example an 
 * HttpServlet could delegate to a Server instance passing the Conversation it constructed from the HttpServletRequest
 * and HttpServletResponse in a similar way that our Delegator implementation of HttpHandler delegates to the Servers.
 *
 * <pre>
 * Usage:  QpidRestAPI [options]
 *
 * Options:
 *        -h,             --help
 *                        show this help message and exit
 *        -a &lt;address&gt;,   --broker-addr=&lt;address&gt;
 *                         broker-addr is in the form:  [username/password@]
 *                         hostname | ip-address [:&lt;port&gt;]   ex:  localhost,
 *                         10.1.1.7:10000, broker-host:10000,
 *                         guest/guest@localhost
 *                         Default is the host QpidRestAPI runs on &amp; port 5672.
 *        -i &lt;address&gt;,   --addr=&lt;address&gt;
 *                        the hostname of the QpidRestAPI default is the wildcard address
 *                        (Bind to a specific address on a multihomed host)
 *        -p &lt;port&gt;,      --port=&lt;port&gt;
 *                        the port the QpidRestAPI is bound to default is 8080
 *        -b &lt;backlog&gt;,   --backlog=&lt;backlog&gt;
 *                        the socket backlog default is 10
 *        -w &lt;directory&gt;, --webroot=&lt;directory&gt;
 *                        the directory of the QpidRestAPI Web Site default is qpid-web
 * </pre>
 * @author Fraser Adams
 */
public class QpidRestAPI
{
    private static final String _usage =
    "Usage: QpidRestAPI [options]\n";

    private static final String _description =
    "Creates an HTTP REST interface to enable us to send messages to Qpid brokers and use QMF2 via HTTP.\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit.\n" +
    "  -a <address>, --broker-addr=<address>\n" +
    "                        broker-addr is in the form:  [username/password@]\n" +
    "                        hostname | ip-address [:&lt;port&gt;] e.g.\n" + 
    "                        localhost, 10.1.1.7:10000, broker-host:10000,\n" +
    "                        guest/guest@localhost, guest/guest@broker-host:10000\n" +
    "                        default is the host QpidRestAPI runs on & port 5672.\n" +
    "  -i <address>, --addr=<address>\n" +
    "                        the hostname of the QpidRestAPI.\n" +
    "                        default is the wildcard address.\n" +
    "                        (Bind to a specific address on a multihomed host).\n" +
    "  -p <port>,    --port=<port>\n" +
    "                        the port the QpidRestAPI is bound to.\n" +
    "                        default is 8080.\n" +
    "  -b <backlog>, --backlog=<backlog>\n" +
    "                        the socket backlog.\n" +
    "                        default is 10\n" +
    "  -w <directory>, --webroot=<directory>\n" +
    "                        the directory of the QpidRestAPI Web Site.\n" +
    "                        default is qpid-web.\n";


    /**
     * Construct and start an instance of QpidRestAPI. This class used a Delegator class to delegate to underlying 
     * Server instances that actually implement the business logic of the REST API.
     *
     * @param addr the the address the QpidRestAPI is bound to (null = default). 
     * @param port the port the QpidRestAPI is bound to.
     * @param broker the address of the Qpid broker to connect to (null = default).
     * @param backlog the socket backlog.
     * @param webroot the directory of the QpidRestAPI Web Site.
     */
    public QpidRestAPI(final String addr, final int port, String broker, final int backlog, final String webroot) 
        throws IOException
    {
        final InetSocketAddress inetaddr = (addr == null) ? new InetSocketAddress(port) :
                                                            new InetSocketAddress(addr, port);
        final HttpServer server = HttpServer.create(inetaddr, backlog);

        broker = (broker == null) ? inetaddr.getAddress().getHostAddress() + ":5672" : broker;

        Delegator fileserver = new Delegator(new FileServer(webroot + "/web", true));
        Delegator qpidserver  = new Delegator(new QpidServer(broker));

        Authenticator authenticator = new Authenticator(this.getClass().getCanonicalName(), webroot + "/authentication");

        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", fileserver);
        server.createContext("/ui", fileserver).setAuthenticator(authenticator);
        server.createContext("/qpid/connection", qpidserver).setAuthenticator(authenticator);
        server.start();
    }

    /**
     * Runs QpidRestAPI.
     * @param args the command line arguments.
     */
    public static void main(String[] args) throws IOException
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        String[] longOpts = {"help", "host=", "port=", "backlog=", "webroot="};
        try
        {
            String addr = null;
            int port = 8080;
            String broker = null;
            int backlog = 10;
            String webroot = "qpid-web";

            GetOpt getopt = new GetOpt(args, "ha:i:p:b:w:", longOpts);
            List<String[]> optList = getopt.getOptList();
            String[] cargs = {};
            cargs = getopt.getEncArgs().toArray(cargs);

            for (String[] opt : optList)
            {
                if (opt[0].equals("-h") || opt[0].equals("--help"))
                {
                    System.out.println(_usage);
                    System.out.println(_description);
                    System.out.println(_options);
                    System.exit(1);
                }
                else if (opt[0].equals("-a") || opt[0].equals("--broker-addr"))
                {
                    broker = opt[1];
                }
                else if (opt[0].equals("-i") || opt[0].equals("--addr"))
                {
                    addr = opt[1];
                }
                else if (opt[0].equals("-p") || opt[0].equals("--port"))
                {
                    port = Integer.parseInt(opt[1]);
                }
                else if (opt[0].equals("-b") || opt[0].equals("--backlog"))
                {
                    backlog = Integer.parseInt(opt[1]);
                }
                else if (opt[0].equals("-w") || opt[0].equals("--webroot"))
                {
                    webroot = opt[1];
                }
            }

            QpidRestAPI restAPI = new QpidRestAPI(addr, port, broker, backlog, webroot);
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(_usage);
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
}

