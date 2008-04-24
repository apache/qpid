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
package org.apache.qpid.sasl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.security.auth.callback.*;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import org.apache.log4j.Logger;

import org.apache.qpid.util.PrettyPrintingUtils;

/**
 * Implements a factory for generating Sasl client implementations.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a list of supported encryption mechansims that meet a defined set of Sasl properties.
 * <tr><td> Provide the best matching supported Sasl mechanism to a preference ordered list of mechanisms and Sasl
 *          properties.
 * <tr><td> Perform username and password request call backs. <td> CallBackHandler
 * </table>
 */
public class ClientFactoryImpl implements SaslClientFactory
{
    //private static final Logger log = Logger.getLogger(ClientFactoryImpl.class);

    /** Holds the names of the supported encryption mechanisms. */
    private static final String[] SUPPORTED_MECHANISMS = { "CRAM-MD5", "PLAIN" };

    /** Defines index of the CRAM-MD5 mechanism within the supported mechanisms. */
    private static final int CRAM_MD5 = 0;

    /** Defines index of the PLAIN mechanism within the supported mechanisms. */
    private static final int PLAIN = 1;

    /** Bit mapping of the no plain text policy. */
    private static final int NOPLAINTEXT = 0x0001;

    /** Bit mapping of the no susceptible active attacks policy. */
    private static final int NOACTIVE = 0x0002;

    /** Bit mapping of the no susceptible to dictionary attacks policy. */
    private static final int NODICTIONARY = 0x0004;

    /** Bit mapping of the must use forward secrecy between sessions policy. */
    private static final int FORWARD_SECRECY = 0x0008;

    /** Bit mapping of the no anonymous logins policy. */
    private static final int NOANONYMOUS = 0x0010;

    /** Bit mapping of the must pass credentials policy. */
    private static final int PASS_CREDENTIALS = 0x0020;

    /** Defines a mapping from supported mechanisms to supported policy flags. */
    private static final int[] SUPPPORTED_MECHANISMS_POLICIES =
        {
            NOPLAINTEXT | NOANONYMOUS, // CRAM-MD5
            NOANONYMOUS // PLAIN
        };

    /**
     * Creates a SaslClient using the parameters supplied.
     *
     * @param mechanisms      The non-null list of mechanism names to try. Each is the IANA-registered name of a SASL
     *                        mechanism. (e.g. "GSSAPI", "CRAM-MD5").
     * @param authorizationId The possibly null protocol-dependent identification to be used for authorization.
     *                        If null or empty, the server derives an authorization ID from the client's authentication
     *                        credentials. When the SASL authentication completes successfully, the specified entity is
     *                        granted access.
     * @param protocol        The non-null string name of the protocol for which the authentication is being performed
     *                        (e.g., "ldap").
     * @param serverName      The non-null fully qualified host name of the server to authenticate to.
     * @param props           The possibly null set of properties used to select the SASL mechanism and to configure the
     *                        authentication exchange of the selected mechanism. See the <tt>Sasl</tt> class for a list
     *                        of standard properties. Other, possibly mechanism-specific, properties can be included.
     *                        Properties not relevant to the selected mechanism are ignored.
     * @param cbh             The possibly null callback handler to used by the SASL mechanisms to get further
     *                        information from the application/library to complete the authentication. For example, a
     *                        SASL mechanism might require the authentication ID, password and realm from the caller.
     *                        The authentication ID is requested by using a <tt>NameCallback</tt>.
     *                        The password is requested by using a <tt>PasswordCallback</tt>.
     *                        The realm is requested by using a <tt>RealmChoiceCallback</tt> if there is a list
     *                        of realms to choose from, and by using a <tt>RealmCallback</tt> if
     *                        the realm must be entered.
     *
     * @return A possibly null <tt>SaslClient</tt> created using the parameters supplied. If null, this factory cannot
     *         produce a <tt>SaslClient</tt> using the parameters supplied.
     *
     * @throws javax.security.sasl.SaslException If cannot create a <tt>SaslClient</tt> because of an error.
     */
    public SaslClient createSaslClient(String[] mechanisms, String authorizationId, String protocol, String serverName,
                                       Map props, CallbackHandler cbh) throws SaslException
    {
        /*log.debug("public SaslClient createSaslClient(String[] mechanisms = " + PrettyPrintingUtils.printArray(mechanisms)
                  + ", String authorizationId = " + authorizationId + ", String protocol = " + protocol
                  + ", String serverName = " + serverName + ", Map props = " + props + ", CallbackHandler cbh): called");*/

        // Get a list of all supported mechanisms that matched the required properties.
        String[] matchingMechanisms = getMechanismNames(props);
        //log.debug("matchingMechanisms = " + PrettyPrintingUtils.printArray(matchingMechanisms));

        // Scan down the list of mechanisms until the first one that matches one of the matching supported mechanisms
        // is found.
        String chosenMechanism = null;

        for (int i = 0; i < mechanisms.length; i++)
        {
            String mechanism = mechanisms[i];

            for (int j = 0; j < matchingMechanisms.length; j++)
            {
                String matchingMechanism = matchingMechanisms[j];

                if (mechanism.equals(matchingMechanism))
                {
                    chosenMechanism = mechanism;

                    break;
                }
            }

            // Stop scanning if a match has been found.
            if (chosenMechanism != null)
            {
                break;
            }
        }

        // Check that a matching mechanism was found or return null otherwise.
        if (chosenMechanism == null)
        {
            //log.debug("No matching mechanism could be found.");

            return null;
        }

        // Instantiate an appropriate client type for the chosen mechanism.
        if (chosenMechanism.equals(SUPPORTED_MECHANISMS[CRAM_MD5]))
        {
            Object[] uinfo = getUserInfo("CRAM-MD5", authorizationId, cbh);

            //log.debug("Using CRAM-MD5 mechanism.");

            return new CramMD5Client((String) uinfo[0], (byte[]) uinfo[1]);
        }
        else
        {
            Object[] uinfo = getUserInfo("PLAIN", authorizationId, cbh);

            //log.debug("Using PLAIN mechanism.");

            return new PlainClient(authorizationId, (String) uinfo[0], (byte[]) uinfo[1]);
        }
    }

    /**
     * Returns an array of names of mechanisms that match the specified
     * mechanism selection policies.
     *
     * @param props The possibly null set of properties used to specify the
     *              security policy of the SASL mechanisms. For example, if <tt>props</tt>
     *              contains the <tt>Sasl.POLICY_NOPLAINTEXT</tt> property with the value
     *              <tt>"true"</tt>, then the factory must not return any SASL mechanisms
     *              that are susceptible to simple plain passive attacks.
     *              See the <tt>Sasl</tt> class for a complete list of policy properties.
     *              Non-policy related properties, if present in <tt>props</tt>, are ignored.
     *
     * @return A non-null array containing a IANA-registered SASL mechanism names.
     */
    public String[] getMechanismNames(Map props)
    {
        //log.debug("public String[] getMechanismNames(Map props = " + props + "): called");

        // Used to build up the valid mechanisms in.
        List validMechanisms = new ArrayList();

        // Transform the Sasl properties into a set of bit mapped flags indicating the required properties of the
        // encryption mechanism employed.
        int requiredFlags = bitMapSaslProperties(props);
        //log.debug("requiredFlags = " + requiredFlags);

        // Scan down the list of supported mechanisms filtering in only those that satisfy all of the desired
        // encryption properties.
        for (int i = 0; i < SUPPORTED_MECHANISMS.length; i++)
        {
            int mechanismFlags = SUPPPORTED_MECHANISMS_POLICIES[i];
            //log.debug("mechanismFlags = " + mechanismFlags);

            // Check if the current mechanism contains all of the required flags.
            if ((requiredFlags & ~mechanismFlags) == 0)
            {
                //log.debug("Mechanism " + SUPPORTED_MECHANISMS[i] + " meets the required properties.");
                validMechanisms.add(SUPPORTED_MECHANISMS[i]);
            }
        }

        String[] result = (String[]) validMechanisms.toArray(new String[validMechanisms.size()]);

        //log.debug("result = " + PrettyPrintingUtils.printArray(result));

        return result;
    }

    /**
     * Transforms a set of Sasl properties, defined using the property names in javax.security.sasl.Sasl, into
     * a bit mapped set of property flags encoded using the bit mapping constants defined in this class.
     *
     * @param properties The Sasl properties to bit map.
     *
     * @return A set of bit mapped properties encoded in an integer.
     */
    private int bitMapSaslProperties(Map properties)
    {
        //log.debug("private int bitMapSaslProperties(Map properties = " + properties + "): called");

        int result = 0;

        // No flags set if no properties are set.
        if (properties == null)
        {
            return result;
        }

        if ("true".equalsIgnoreCase((String) properties.get(Sasl.POLICY_NOPLAINTEXT)))
        {
            result |= NOPLAINTEXT;
        }

        if ("true".equalsIgnoreCase((String) properties.get(Sasl.POLICY_NOACTIVE)))
        {
            result |= NOACTIVE;
        }

        if ("true".equalsIgnoreCase((String) properties.get(Sasl.POLICY_NODICTIONARY)))
        {
            result |= NODICTIONARY;
        }

        if ("true".equalsIgnoreCase((String) properties.get(Sasl.POLICY_NOANONYMOUS)))
        {
            result |= NOANONYMOUS;
        }

        if ("true".equalsIgnoreCase((String) properties.get(Sasl.POLICY_FORWARD_SECRECY)))
        {
            result |= FORWARD_SECRECY;
        }

        if ("true".equalsIgnoreCase((String) properties.get(Sasl.POLICY_PASS_CREDENTIALS)))
        {
            result |= PASS_CREDENTIALS;
        }

        return result;
    }

    /**
     * Uses the specified call back handler to query for the users log in name and password.
     *
     * @param prefix          A prefix to prepend onto the username and password queries.
     * @param authorizationId The default autorhization name.
     * @param cbh             The call back handler.
     *
     * @return The username and password from the callback.
     *
     * @throws SaslException If the callback fails for any reason.
     */
    private Object[] getUserInfo(String prefix, String authorizationId, CallbackHandler cbh) throws SaslException
    {
        // Check that the callback handler is defined.
        if (cbh == null)
        {
            throw new SaslException("Callback handler to get username/password required.");
        }

        try
        {
            String userPrompt = prefix + " authentication id: ";
            String passwdPrompt = prefix + " password: ";

            NameCallback ncb =
                (authorizationId == null) ? new NameCallback(userPrompt) : new NameCallback(userPrompt, authorizationId);
            PasswordCallback pcb = new PasswordCallback(passwdPrompt, false);

            // Ask the call back handler to get the users name and password.
            cbh.handle(new Callback[] { ncb, pcb });

            char[] pw = pcb.getPassword();

            byte[] bytepw;
            String authId;

            if (pw != null)
            {
                bytepw = new String(pw).getBytes("UTF8");
                pcb.clearPassword();
            }
            else
            {
                bytepw = null;
            }

            authId = ncb.getName();

            return new Object[] { authId, bytepw };
        }
        catch (IOException e)
        {
            throw new SaslException("Cannot get password.", e);
        }
        catch (UnsupportedCallbackException e)
        {
            throw new SaslException("Cannot get userid/password.", e);
        }
    }
}
