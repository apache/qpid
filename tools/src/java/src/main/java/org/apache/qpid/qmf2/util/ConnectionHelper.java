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
package org.apache.qpid.qmf2.util;

// JMS Imports
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.JMSException;

// JNDI Imports
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Misc Imports
import java.util.Map;
import java.util.Properties;

// Reuse this class as it provides a handy mechanism to parse an options String into a Map
import org.apache.qpid.messaging.util.AddressParser;

/**
 * The Qpid M4 Java and C++ clients and the Python QMF tools all use different URL formats.
 * This class provides helper methods to support a variety of URL formats and connection options
 * in order to provide flexibility when creating connections.
 * <p>
 * Much of the following information is taken from <a href="https://cwiki.apache.org/qpid/url-format-proposal.html">
 * New URL format for AMQP + Qpid</a>
 * <p>
 * <h3>AMQP 0-10 format</h3>
 * C++ uses the AMQP 0-10 format: section 9.1.2 as follows:
 * <pre>
 * amqp_url       = "amqp:" prot_addr_list
 * prot_addr_list = [prot_addr ","]* prot_addr
 * prot_addr      = tcp_prot_addr | tls_prot_addr
 *
 * tcp_prot_addr  = tcp_id tcp_addr
 * tcp_id         = "tcp:" | ""
 * tcp_addr       = [host [":" port] ]
 * host           = &lt;as per <a href="http://www.ietf.org/rfc/rfc3986.txt">rfc3986</a>&gt;
 * port           = number
 * </pre>
 * The AMQP 0-10 format only provides protocol address information for a (list of) brokers.
 * <p>
 * <p>
 *
 * <h3>Python tool BrokerURL format</h3>
 * The Python tools bundled with Qpid such as qpid-config use a "BrokerURL" format with the following Address syntax:
 * <pre>
 * [&lt;user&gt;/&lt;pass&gt;@]&lt;hostname&gt; | &lt;ip-address&gt;[:&lt;port&gt;]
 * </pre>
 *
 * <h3>Qpid M4 Java Connection URL format</h3>
 * The Qpid M4 Java format provides additional options for connection options (user, password, vhost etc.)
 * Documentation for this format may be found here: <a href="https://cwiki.apache.org/qpid/connection-url-format.html">
 * Qpid M4 Java Connection URL Format</a>
 * <p>
 * Java ConnectionURLs look like this:
 * <pre>
 * amqp://[&lt;user&gt;:&lt;pass&gt;@][&lt;clientid&gt;]/&lt;virtualhost&gt;[?&lt;option&gt;='&lt;value&gt;'[&&lt;option&gt;='&lt;value&gt;']]
 * </pre>
 * This syntax is very powerful, but it can also be fairly complex to work with, especially when one realises
 * that one of the options in the above syntax is brokerlist='&lt;broker url&gt;' where broker url is itself a URL
 * of the format:
 * <pre>
 * &lt;transport&gt;://&lt;host&gt;[:&lt;port&gt;][?&lt;option&gt;='&lt;value&gt;'[&&lt;option&gt;='&lt;value&gt;']]
 * </pre>
 * so one may see ConnectionURLs that look like:
 * <pre>
 * amqp://guest:guest@clientid/test?brokerlist='tcp://localhost:5672?retries='10'&connectdelay='1000''
 * </pre>
 *
 * <p>
 * <p>
 * <h3>Extended AMQP 0-10 URL format</h3>
 * There is a proposal to extend the AMQP 0-10 URL syntax to include user:pass@ style authentication
 * information, virtual host and extensible name/value options. It also makes the implied extension points of
 * the original grammar more explicit.
 * <pre>
 * amqp_url        = "amqp://" [ userinfo "@" ] addr_list [ vhost ]
 * addr_list       = addr *( "," addr )
 * addr            = prot_addr [ options ]
 * prot_addr       = tcp_prot_addr | other_prot_addr
 * vhost           = "/" *pchar [ options ]
 *
 * tcp_prot_addr   = tcp_id tcp_addr
 * tcp_id          = "tcp:" / ""	; tcp is the default
 * tcp_addr        = [ host [ ":" port ] ]
 *
 * other_prot_addr = other_prot_id ":" *pchar
 * other_prot_id   = scheme
 *
 * options         = "?" option *( ";" option )
 * option          = name "=" value
 * name            = *pchar
 * value           = *pchar
 * </pre>
 *
 * <h3>Incompatibility with AMQP 0-10 format</h3>
 * This syntax is backward compatible with AMQP 0-10 with one exception: AMQP 0-10 did not have an initial
 * // after amqp: The justification was that that the // form is only used for URIs with hierarchical structure
 * <p>
 * However it's been pointed out that in fact the URL does already specify a 1-level hierarchy of address / vhost.
 * In the future the hierarchy could be extended to address objects within a vhost such as queues, exchanges etc.
 * So this proposal adopts amqp:// syntax.
 * <p>
 * It's easy to write a backward-compatible parser by relaxing the grammar as follows:
 * <pre>
 * amqp_url = "amqp:" [ "//" ] [ userinfo "@" ] addr_list [ vhost ]
 * </pre>
 *
 * <h3>Differences from Qpid M4 Java Connection URL format</h3>
 * Addresses are at the start of the URL rather than in the "brokerlist" option.
 * <p>
 * Option format is ?foo=bar;x=y rather than ?foo='bar'&x='y'. The use of "'" quotes is not common for URI query
 * strings. The use of "&" as a separator creates problems
 * <p>
 * user, pass and clientid are options rather than having a special place at the front of the URL. clientid is
 * a Qpid proprietary property and user/pass are not relevant in all authentication schemes.
 * <p>
 * Qpid M4 Java URLs requires the brokerlist option, so this is an easy way to detect a Qpid M4 URL vs. an
 * Extended AMQP 0-10 URL and parse accordingly.
 *
 * <h3>Options</h3>
 * Some of the URL forms are fairly limited in terms of options, so it is useful to be able to pass options as
 * an additional string, though it's important to note that if multiple brokers are supplied in the AMQP 0.10 format
 * the same options will be applied to all brokers.
 * <p>
 * The option format is the same as that of the C++ qpid::messaging Connection class. for example: "{reconnect: true,
 * tcp-nodelay: true}":
 * <p>
 * <table summary="Connection Options" width="100%" border="1"><colgroup><col><col><col></colgroup><thead>
 * <tr><th>option name</th><th>value type</th><th>semantics</th></tr></thead><tbody>
 * <tr>
 *      <td><code class="literal">maxprefetch</code></td>
 *      <td>integer</td>
 *      <td>The maximum number of pre-fetched messages per destination.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sync_publish</code></td>
 *      <td>{'persistent' | 'all'}</td>
 *      <td>A sync command is sent after every persistent message to guarantee that it has been received; if the
 *          value is 'persistent', this is done only for persistent messages.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sync_ack</code></td>
 *      <td>boolean</td>
 *      <td>A sync command is sent after every acknowledgement to guarantee that it has been received.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">use_legacy_map_msg_format</code></td>
 *      <td>boolean</td>
 *      <td>If you are using JMS Map messages and deploying a new client with any JMS client older than 0.8 release,
 *          you must set this to true to ensure the older clients can understand the map message encoding.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">failover</code></td>
 *      <td>{'roundrobin' | 'singlebroker' | 'nofailover' | 'failover_exchange'}</td>
 *      <td>If roundrobin is selected it will try each broker given in the broker list. If failover_exchange is
 *          selected it connects to the initial broker given in the broker URL and will receive membership updates
 *          via the failover exchange. </td>
 * </tr>
 * <tr>
 *      <td><code class="literal">cyclecount</code></td>
 *      <td>integer</td>
 *      <td>For roundrobin failover cyclecount is the number of times to loop through the list of available brokers
 *          before failure.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">username</code></td>
 *      <td>string</td>
 *      <td>The username to use when authenticating to the broker.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">password</code></td>
 *      <td>string</td>
 *      <td>The password to use when authenticating to the broker.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sasl_mechanisms</code></td>
 *      <td>string</td>
 *      <td>The specific SASL mechanisms to use when authenticating to the broker. The value is a space separated list.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sasl_mechs</code></td>
 *      <td>string</td>
 *      <td>The specific SASL mechanisms to use when authenticating to the broker. The value is a space separated
 *          is a space separated list. This is simply a synonym for sasl_mechanisms above</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sasl_encryption</code></td>
 *      <td>boolean</td>
 *      <td>If <code class="literal">sasl_encryption='true'</code>, the JMS client attempts to negotiate a security
 *          layer with the broker using GSSAPI to encrypt the connection. Note that for this to happen, GSSAPI must
 *          be selected as the sasl_mech.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">ssl</code></td>
 *      <td>boolean</td>
 *      <td>If <code class="literal">ssl='true'</code>, the JMS client will encrypt the connection using SSL.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">reconnect</code></td>
 *      <td>boolean</td>
 *      <td>Transparently reconnect if the connection is lost.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">reconnect_timeout</code></td>
 *      <td>integer</td>
 *      <td>Total number of seconds to continue reconnection attempts before giving up and raising an exception.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">reconnect_limit</code></td>
 *      <td>integer</td>
 *      <td>Maximum number of reconnection attempts before giving up and raising an exception.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">reconnect_interval_min</code></td>
 *      <td>integer representing time in seconds</td>
 *      <td> Minimum number of seconds between reconnection attempts. The first reconnection attempt is made
 *           immediately; if that fails, the first reconnection delay is set to the value of <code class="literal">
 *           reconnect_interval_min</code>; if that attempt fails, the reconnect interval increases exponentially
 *           until a reconnection attempt succeeds or <code class="literal">reconnect_interval_max</code> is reached.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">reconnect_interval_max</code></td>
 *      <td>integer representing time in seconds</td>
 *      <td>Maximum reconnect interval.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">reconnect_interval</code></td>
 *      <td>integer representing time in seconds</td>
 *      <td>Sets both <code class="literal">reconnection_interval_min</code> and <code class="literal">
 *          reconnection_interval_max</code> to the same value. The default value is 5 seconds</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">heartbeat</code></td>
 *      <td>integer representing time in seconds</td>
 *      <td>Requests that heartbeats be sent every N seconds. If two successive heartbeats are missed the connection is
 *	        considered to be lost.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">protocol</code></td>
 *      <td>string</td>
 *      <td>Sets the underlying protocol used. The default option is 'tcp'. To enable ssl, set to 'ssl'. The C++ client 
 *          additionally supports 'rdma'. </td>
 * </tr>
 * <tr>
 *      <td><code class="literal">tcp-nodelay</code></td>
 *      <td>boolean</td>
 *      <td>Set tcp no-delay, i.e. disable Nagle algorithm.</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sasl_protocol</code></td>
 *      <td>string</td>
 *      <td>Used only for Kerberos. <code class="literal">sasl_protocol</code> must be set to the principal for the
 *          qpidd broker, e.g. <code class="literal">qpidd/</code></td>
 * </tr>
 * <tr>
 *      <td><code class="literal">sasl_server</code></td>
 *      <td>string</td>
 *      <td>For Kerberos, sasl_mechs must be set to GSSAPI, <code class="literal">sasl_server</code> must be set to
 *          the host for the SASL server, e.g. <code class="literal">sasl.com.</code></td>
 * </tr>
 * <tr>
 *      <td><code class="literal">trust_store</code></td>
 *      <td>string</td>
 *      <td>path to Keberos trust store</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">trust_store_password</code></td>
 *      <td>string</td>
 *      <td>Kerberos trust store password</td>
 * </tr>
 * <tr>
 *      <td><code class="key_store</code></td>
 *      <td>string</td>
 *      <td>path to Kerberos key store </td>
 * </tr>
 * <tr>
 *      <td><code class="literal">key_store_password</code></td>
 *      <td>string</td>
 *      <td>Kerberos key store password</td>
 * </tr>
 * <tr>
 *      <td><code class="literal">ssl_cert_alias</code></td>
 *      <td>string</td>
 *      <td>If multiple certificates are present in the keystore, the alias will be used to extract the correct
 *          certificate.</td>
 * </tr>
 * </tbody></table>
 *
 * <h3>Other features of this class</h3>
 * Whilst it is generally the norm to use JNDI to specify Connections, Destinations etc. it is also often quite useful
 * to specify Connections programmatically, for example when writing a tool one may wish to specify the broker via the 
 * command line to enable the tool to connect to different broker instances.
 * To facilitate this this class provides a basic createConnection() method that takes a URL and returns a JMS 
 * Connection.
 *
 * @author Fraser Adams
 */
public final class ConnectionHelper
{
    private static final Logger _log = LoggerFactory.getLogger(ConnectionHelper.class);

    /**
     * Make constructor private as the class comprises only static helper methods.
     */
    private ConnectionHelper()
    {
    }

    /**
     * Create a ConnectionURL from the proposed Extended AMQP 0.10 URL format. This is experimental and may or may
     * not work. Options are assumed to be the same as the Java Connection URL, which will probably not be the case
     * if this URL form is ultimately adopted. For example the example URLs have "amqp://host1,host2?retry=2,host3"
     * whereas the Java Connection URL uses &retries=2
     *
     * I'm not overly keen on this code it looks pretty inelegant and I'm slightly embarrassed by it, but it
     * is really just an experiment.
     *
     * @param url the input URL.
     * @param username the username.
     * @param password the password.
     * @return a String containing the Java Connection URL.
     */
    private static String parseExtendedAMQPURL(String url, String username, String password)
    {
        String vhost = ""; // Specifying an empty vhost uses default Virtual Host.
        String urlOptions = "";
        String brokerList = "";

        url = url.substring(7);          // Chop off "amqp://"
        String[] split = url.split("@"); // First break out the userinfo if present
        String remainder = split[0];
        if (split.length == 2)
        { // Extract username and password from the userinfo field
            String[] userinfo = split[0].split(":");
            remainder = split[1];

            username = userinfo[0];
            if (userinfo.length == 2)
            {
                password = userinfo[1];
            }
        }

        // Replace foo=baz with foo='baz'. There's probably a more elegant way to do this using a fancy
        // regex, but unfortunately I'm not terribly good at regexes so this is the brute force approach :-(
        // OTOH it's probably more readable and obvious than a regex to do the same thing would be.
        split = remainder.split("=");
        StringBuilder buf = new StringBuilder(split[0]);
        for (int i = 1; i < split.length; i++)
        {
            String substring = "='" + split[i];
            if (substring.contains(";"))
            {
                substring = substring.replaceFirst(";", "'&"); // Note we also replace the option separator here
            }
            else if (substring.contains("/"))
            {
                substring = substring.replaceFirst("/", "'/");
            }
            else if (substring.contains(","))
            {
                substring = substring.replaceFirst(",", "',");
            }
            else
            {
                substring = substring + "'";
            }
            buf.append(substring);
        }
        remainder = buf.toString();

        // Now split into addrList and vhost parts (see Javadoc for the grammar of this URL format)
        split = remainder.split("/");             // vhost starts with a mandatory '/' character
        String[] addrSplit = split[0].split(","); // prot_addrs are comma separated
        boolean firstBroker = true;
        buf = new StringBuilder();
        for (String broker : addrSplit)
        { // Iterate through the address list creating brokerList style URLs
            broker = broker.trim();
            String protocol = "tcp"; // set default protocol
            String[] components = broker.split(":");

            // Note protocols other than tcp and vm are not supported by the Connection URL so the results
            // are pretty much undefined if other protocols are passed on the input URL.
            if (components.length == 1)
            { // Assume protocol = tcp and broker = hostname
            }
            else if (components.length == 2)
            { // Probably host:port but could be some other protocol in and Extended AMQP 0.10 URL
                try 
                { // Somewhat ugly, but effective test to check if the second component is an integer
                    Integer.parseInt(components[1]);
                    // If the above succeeds the components are likely host:port
                    broker = components[0] + ":" + components[1];
                }
                catch (NumberFormatException nfe)
                { // If the second component isn't an integer then it's likely a wacky protocol...
                    protocol = components[0];
                    broker = components[1];
                }
            }
            else if (components.length == 3)
            {
                protocol = components[0];
                broker = components[1] + ":" + components[2];
            }

            if (firstBroker)
            {
                buf.append(protocol + "://" + broker);
            }
            else
            { // https://cwiki.apache.org/qpid/connection-url-format.html says "A minimum of one broker url is
              // required additional URLs are semi-colon(';') delimited."
                buf.append(";" + protocol + "://" + broker);
            }
            firstBroker = false;
        }
        brokerList = "'" + buf.toString() + "'";

        if (split.length == 2)
        { // Extract the vhost and any connection level options
            vhost = split[1];
            String[] split2 = vhost.split("\\?"); // Look for options
            vhost = split2[0];
            if (split2.length == 2)
            {
                urlOptions = "&" + split2[1];
            }
        }
        
        String connectionURL = "amqp://" + username + ":" + password + "@QpidJMS/" + vhost + "?brokerlist=" + 
                                brokerList + urlOptions;
        return connectionURL;
    }

    /**
     * If no explicit username is supplied then explicitly set sasl mechanism to ANONYMOUS. If this isn't done
     * The default is PLAIN which causes the broker to fail with "warning Failed to retrieve sasl username".
     *
     * @param username the previously extracted username.
     * @param brokerListOptions the brokerList options extracted so far.
     * @return the brokerList options adjusted with sasl_mechs='ANONYMOUS' if no username has been supplied.
     */
    private static String adjustBrokerListOptions(final String username, final String brokerListOptions)
    {
        if (username.equals(""))
        {
            if (brokerListOptions.equals(""))
            {
                return "?sasl_mechs='ANONYMOUS'";
            }
            else
            {
                if (brokerListOptions.contains("sasl_mechs"))
                {
                    return brokerListOptions;
                }
                else
                {
                    return brokerListOptions + "&sasl_mechs='ANONYMOUS'";
                }
            }
        }
        else
        {
            return brokerListOptions;
        }
    }

    /**
     * Create a ConnectionURL from the input "generic" URL.
     *
     * @param url the input URL.
     * @param username the username.
     * @param password the password.
     * @param urlOptions the pre-parsed set of connection level options.
     * @param brokerListOptions the pre-parsed set of specific brokerList options.
     * @return a String containing the Java Connection URL.
     */
    private static String parseURL(String url, String username, String password,
                                   String urlOptions, String brokerListOptions)
    {
        if (url.startsWith("amqp://"))
        { // Somewhat experimental. This new format is only a "proposed" format
            return parseExtendedAMQPURL(url, username, password);
        }

        String vhost = ""; // Specifying an empty vhost uses default Virtual Host.
        String brokerList = "";
        if (url.startsWith("amqp:"))
        { // AMQP 0.10 URL format
            url = url.substring(5);              // Chop off "amqp:"
            String[] addrSplit = url.split(","); // prot_addrs are comma separated
            boolean firstBroker = true;
            brokerListOptions = adjustBrokerListOptions(username, brokerListOptions);
            StringBuilder buf = new StringBuilder();
            for (String broker : addrSplit)
            { // Iterate through the address list creating brokerList style URLs
                broker = broker.trim();
                if (broker.startsWith("tcp:"))
                { // Only tcp is supported in an AMQP 0.10 prot_addr so we *should* only have to account for
                  // a "tcp:" prefix when normalising broker URLs
                    broker = broker.substring(4); // Chop off "tcp:"
                }

                if (firstBroker)
                {
                    buf.append("tcp://" + broker + brokerListOptions);
                }
                else
                { // https://cwiki.apache.org/qpid/connection-url-format.html says "A minimum of one broker url is
                  // required additional URLs are semi-colon(';') delimited."
                    buf.append(";tcp://" + broker + brokerListOptions);
                }
                firstBroker = false;
            }
            brokerList = "'" + buf.toString() + "'";
        }
        else if (url.contains("@"))
        { // BrokerURL format
            String[] split = url.split("@");
            url = split[1];

            split = split[0].split("/");
            username = split[0];

            if (split.length == 2)
            {
                password = split[1];
            }

            brokerListOptions = adjustBrokerListOptions(username, brokerListOptions);
            brokerList = "'tcp://" + url + brokerListOptions + "'";
        }
        else
        { // Basic host:port format
            brokerListOptions = adjustBrokerListOptions(username, brokerListOptions);
            brokerList = "'tcp://" + url + brokerListOptions + "'";
        }

        String connectionURL = "amqp://" + username + ":" + password + "@QpidJMS/" + vhost + "?brokerlist=" + 
                                brokerList + urlOptions;
        return connectionURL;
    }


    /**
     * Creates a Java Connection URL from one of the other supported URL formats.
     *
     * @param url an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Connection URL (the latter is simply 
     *        returned untouched).
     * @return a String containing the Java Connection URL.
     */
    public static String createConnectionURL(String url)
    {
        return createConnectionURL(url, null);
    }

    /**
     * Creates a Java Connection URL from one of the other supported URL formats plus options.
     *
     * @param url an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Connection URL (the latter is simply 
     *        returned untouched).
     * @param opts a String containing the options encoded using the same form as the C++ qpid::messaging
     *        Connection class.
     * @return a String containing the Java Connection URL.
     */
    public static String createConnectionURL(String url, String opts)
    {
        // This method is actually mostly about parsing the options, when the options are extracted it delegates
        // to parseURL() to do the actual URL parsing.

        // If a Java Connection URL has been passed in we simply return it.
        if (url.startsWith("amqp://") && url.contains("brokerlist"))
        {
            return url;
        }

        // Initialise options to default values
        String username = "";
        String password = "";
        String urlOptions = "";
        String brokerListOptions = "";

        // Get options from option String
        if (opts != null && opts.startsWith("{") && opts.endsWith("}"))
        {
            // Connection URL Options
            String maxprefetch = "";
            String sync_publish = "";
            String sync_ack = "";
            String use_legacy_map_msg_format = "";
            String failover = "";

            // Broker List Options
            String heartbeat = "";
            String retries = "";
            String connectdelay = "";
            String connecttimeout = "";

            String tcp_nodelay = "";

            String sasl_mechs = "";
            String sasl_encryption = "";
            String sasl_protocol = "";
            String sasl_server = "";

            String ssl = "";
            String ssl_verify_hostname = "";
            String ssl_cert_alias = "";

            String trust_store = "";
            String trust_store_password = "";
            String key_store = "";
            String key_store_password = "";

            Map options = new AddressParser(opts).map();

            if (options.containsKey("maxprefetch"))
            {
                maxprefetch = "&maxprefetch='" + options.get("maxprefetch").toString() + "'";
            }

            if (options.containsKey("sync_publish"))
            {
                sync_publish = "&sync_publish='" + options.get("sync_publish").toString() + "'";
            }

            if (options.containsKey("sync_ack"))
            {
                sync_ack = "&sync_ack='" + options.get("sync_ack").toString() + "'";
            }

            if (options.containsKey("use_legacy_map_msg_format"))
            {
                use_legacy_map_msg_format = "&use_legacy_map_msg_format='" + 
                                            options.get("use_legacy_map_msg_format").toString() + "'";
            }

            if (options.containsKey("failover"))
            {
                if (options.containsKey("cyclecount"))
                {
                    failover = "&failover='" + options.get("failover").toString() + "?cyclecount='" +
                                options.get("cyclecount").toString() + "''";
                }
                else
                {
                    failover = "&failover='" + options.get("failover").toString() + "'";
                }
            }

            if (options.containsKey("username"))
            {
                username = options.get("username").toString();
            }

            if (options.containsKey("password"))
            {
                password = options.get("password").toString();
            }

            if (options.containsKey("reconnect"))
            {
                String value = options.get("reconnect").toString();
                if (value.equalsIgnoreCase("true"))
                {
                    retries = "&retries='" + Integer.MAX_VALUE + "'";
                    connectdelay = "&connectdelay='5000'";
                }
            }

            if (options.containsKey("reconnect_limit"))
            {
                retries = "&retries='" + options.get("reconnect_limit").toString() + "'";
            }

            if (options.containsKey("reconnect_interval"))
            {
                connectdelay = "&connectdelay='" + options.get("reconnect_interval").toString() + "000'";
            }

            if (options.containsKey("reconnect_interval_min"))
            {
                connectdelay = "&connectdelay='" + options.get("reconnect_interval_min").toString() + "000'";
            }

            if (options.containsKey("reconnect_interval_max"))
            {
                connectdelay = "&connectdelay='" + options.get("reconnect_interval_max").toString() + "000'";
            }

            if (options.containsKey("reconnect_timeout"))
            {
                connecttimeout = "&connecttimeout='" + options.get("reconnect_timeout").toString() + "000'";
            }

            if (options.containsKey("heartbeat"))
            {
                heartbeat = "&heartbeat='" + options.get("heartbeat").toString() + "'";
            }

            if (options.containsKey("tcp-nodelay"))
            {
                tcp_nodelay = "&tcp_nodelay='" + options.get("tcp-nodelay").toString() + "'";
            }

            if (options.containsKey("sasl_mechanisms"))
            {
                sasl_mechs = "&sasl_mechs='" + options.get("sasl_mechanisms").toString() + "'";
            }

            if (options.containsKey("sasl_mechs"))
            {
                sasl_mechs = "&sasl_mechs='" + options.get("sasl_mechs").toString() + "'";
            }

            if (options.containsKey("sasl_encryption"))
            {
                sasl_encryption = "&sasl_encryption='" + options.get("sasl_encryption").toString() + "'";
            }

            if (options.containsKey("sasl_protocol"))
            {
                sasl_protocol = "&sasl_protocol='" + options.get("sasl_protocol").toString() + "'";
            }

            if (options.containsKey("sasl_server"))
            {
                sasl_server = "&sasl_server='" + options.get("sasl_server").toString() + "'";
            }

            if (options.containsKey("trust_store"))
            {
                trust_store = "&trust_store='" + options.get("trust_store").toString() + "'";
            }

            if (options.containsKey("trust_store_password"))
            {
                trust_store_password = "&trust_store_password='" + options.get("trust_store_password").toString() + "'";
            }

            if (options.containsKey("key_store"))
            {
                key_store = "&key_store='" + options.get("key_store").toString() + "'";
            }

            if (options.containsKey("key_store_password"))
            {
                key_store_password = "&key_store_password='" + options.get("key_store_password").toString() + "'";
            }

            if (options.containsKey("protocol"))
            {
                String value = options.get("protocol").toString();
                if (value.equalsIgnoreCase("ssl"))
                {
                    ssl = "&ssl='true'";
                    if (options.containsKey("ssl_verify_hostname"))
                    {
                        ssl_verify_hostname = "&ssl_verify_hostname='" + options.get("ssl_verify_hostname").toString() + "'";
                    }

                    if (options.containsKey("ssl_cert_alias"))
                    {
                        ssl_cert_alias = "&ssl_cert_alias='" + options.get("ssl_cert_alias").toString() + "'";
                    }
                }
            }
        
            urlOptions = maxprefetch + sync_publish + sync_ack + use_legacy_map_msg_format + failover;

            brokerListOptions = heartbeat + retries + connectdelay + connecttimeout + tcp_nodelay +
                                sasl_mechs + sasl_encryption + sasl_protocol + sasl_server +
                                ssl + ssl_verify_hostname + ssl_cert_alias +
                                trust_store + trust_store_password + key_store + key_store_password;

            if (brokerListOptions.startsWith("&"))
            {
                brokerListOptions = "?" + brokerListOptions.substring(1);
            }
        }

        return parseURL(url, username, password, urlOptions, brokerListOptions);
    }

    /**
     * Creates a JMS Connection from one of the supported URL formats.
     *
     * @param url an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Connection URL.
     * @return a javax.jms.Connection.
     */
    public static Connection createConnection(String url)
    {
        return createConnection(url, null);
    }

    /**
     * Creates a JMS Connection from one of the supported URL formats plus options.
     *
     * @param url an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Connection URL.
     * @param opts a String containing the options encoded using the same form as the C++ qpid::messaging
     *        Connection class.
     * @return a javax.jms.Connection.
     */
    public static Connection createConnection(String url, String opts)
    {
        String connectionUrl = createConnectionURL(url, opts);
        _log.info("ConnectionHelper.createConnection() {}", connectionUrl);

        // Initialise JNDI names etc into properties
        Properties props = new Properties();
        props.setProperty("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        props.setProperty("connectionfactory.ConnectionFactory", connectionUrl);

        Connection connection = null;
        try
        {
            Context jndi = new InitialContext(props);
            ConnectionFactory connectionFactory = (ConnectionFactory)jndi.lookup("ConnectionFactory");
            connection = connectionFactory.createConnection();
        }
        catch (NamingException ne)
        {
            _log.info("NamingException {} caught in createConnection()", ne.getMessage());
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in createConnection()", jmse.getMessage());
        }

        return connection;
    }
}

