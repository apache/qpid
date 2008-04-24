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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * Implements the CRAM-MD5 SASL mechanism.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Concatenate the user id and password hashed by a challenge string as the challenge response.
 * <tr><td> Ensure password is wiped once a challenge has been processed.
 * </table>
 */
public class CramMD5Client implements SaslClient
{
    /** Defines the HMAC block size. */
    private static final int MD5_BLOCKSIZE = 64;

    /** Flag used to indicate that the authentication has completed. */
    private boolean completed = false;

    private String authenticationId;
    private byte[] password;

    /**
     * Creates a PLAIN SASL client for an authorization id, authentication id and password.
     *
     * @param authenticationId The authentication id.
     * @param password         The password.
     *
     * @throws SaslException If the authentication id or password is null.
     */
    CramMD5Client(String authenticationId, byte[] password) throws SaslException
    {
        // Check that a username and password are specified.
        if ((authenticationId == null) || (password == null))
        {
            throw new SaslException("CRAM: authentication id and password must be specified");
        }

        // Keep the log in credentials.
        this.authenticationId = authenticationId;
        this.password = password;
    }

    /**
     * Returns the IANA-registered mechanism name of this SASL client. (e.g. "CRAM-MD5", "GSSAPI").
     *
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    public String getMechanismName()
    {
        return "CRAM-MD5";
    }

    /**
     * Determines whether this mechanism has an optional initial response. If true, caller should call
     * <tt>evaluateChallenge()</tt> with an empty array to get the initial response.
     *
     * <p/>CRAM-MD5 has no intial response.
     *
     * @return true if this mechanism has an initial response.
     */
    public boolean hasInitialResponse()
    {
        return false;
    }

    /**
     * Evaluates the challenge data and generates a response. If a challenge is received from the server during the
     * authentication process, this method is called to prepare an appropriate next response to submit to the server.
     *
     * <p/>The initial response for the SASL command, for the CRAM-MD5 mechanism is the concatenation of authentication
     * ID and password HMAC-MD5 hashed by the server challenge.
     *
     * @param challenge The non-null challenge sent from the server. The challenge array may have zero length.
     *
     * @return The possibly null reponse to send to the server. It is null if the challenge accompanied a "SUCCESS"
     *         status and the challenge only contains data for the client to update its state and no response
     *         needs to be sent to the server. The response is a zero-length byte array if the client is to send a
     *         response with no data.
     *
     * @throws javax.security.sasl.SaslException If an error occurred while processing the challenge or generating a
     *                                           response.
     */
    public byte[] evaluateChallenge(byte[] challenge) throws SaslException
    {
        // Check that the authentication has not already been performed.
        if (completed)
        {
            throw new IllegalStateException("CRAM-MD5 authentication already completed.");
        }

        // Check if the password is null, this will be the case if a previous attempt to authenticated failed.
        if (password == null)
        {
            throw new IllegalStateException("CRAM-MD5 authentication previously aborted due to error.");
        }

        // Generate a keyed-MD5 digest from the user's password keyed by the challenge bytes.
        try
        {
            String digest = hmac_md5(password, challenge);
            String result = authenticationId + " " + digest;

            completed = true;

            return result.getBytes("UTF8");
        }
        catch (java.security.NoSuchAlgorithmException e)
        {
            throw new SaslException("MD5 algorithm not available on platform", e);
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            throw new SaslException("UTF8 not available on platform", e);
        }
        finally
        {
            clearPassword();
        }
    }

    /**
     * Determines whether the authentication exchange has completed. This method may be called at any time, but
     * typically, it will not be called until the caller has received indication from the server (in a protocol-specific
     * manner) that the exchange has completed.
     *
     * @return true if the authentication exchange has completed; false otherwise.
     */
    public boolean isComplete()
    {
        return completed;
    }

    /**
     * Unwraps a byte array received from the server. This method can be called only after the authentication exchange
     * has completed (i.e., when <tt>isComplete()</tt> returns true) and only if the authentication exchange has
     * negotiated integrity and/or privacy as the quality of protection; otherwise, an <tt>IllegalStateException</tt> is
     * thrown.
     *
     * <p/><tt>incoming</tt> is the contents of the SASL buffer as defined in RFC 2222 without the leading four octet
     * field that represents the length. <tt>offset</tt> and <tt>len</tt> specify the portion of <tt>incoming</tt>
     * to use.
     *
     * @param incoming A non-null byte array containing the encoded bytes from the server.
     * @param offset   The starting position at <tt>incoming</tt> of the bytes to use.
     * @param len      The number of bytes from <tt>incoming</tt> to use.
     *
     * @return A non-null byte array containing the decoded bytes.
     *
     * @throws javax.security.sasl.SaslException If <tt>incoming</tt> cannot be successfully unwrapped.
     * @throws IllegalStateException If the authentication exchange has not completed, or if the negotiated quality of
     *                               protection has neither integrity nor privacy.
     */
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
    {
        throw new SaslException("CRAM-MD5 does not support quality of protection.");
    }

    /**
     * Wraps a byte array to be sent to the server. This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true) and only if the authentication exchange has negotiated
     * integrity and/or privacy as the quality of protection; otherwise, an <tt>IllegalStateException</tt> is thrown.
     *
     * <p/>The result of this method will make up the contents of the SASL buffer as defined in RFC 2222 without the
     * leading four octet field that represents the length. <tt>offset</tt> and <tt>len</tt> specify the portion of
     * <tt>outgoing</tt> to use.
     *
     * @param outgoing A non-null byte array containing the bytes to encode.
     * @param offset   The starting position at <tt>outgoing</tt> of the bytes to use.
     * @param len      The number of bytes from <tt>outgoing</tt> to use.
     *
     * @return A non-null byte array containing the encoded bytes.
     *
     * @throws javax.security.sasl.SaslException If <tt>outgoing</tt> cannot be successfully wrapped.
     * @throws IllegalStateException If the authentication exchange has not completed, or if the negotiated quality of
     *                               protection has neither integrity nor privacy.
     */
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
    {
        throw new SaslException("CRAM-MD5 does not support quality of protection.");
    }

    /**
     * Retrieves the negotiated property. This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true); otherwise, an <tt>IllegalStateException</tt> is thrown.
     *
     * @param propName The non-null property name.
     *
     * @return The value of the negotiated property. If null, the property was not negotiated or is not applicable to
     *         this mechanism.
     *
     * @throws IllegalStateException If this authentication exchange has not completed.
     */
    public Object getNegotiatedProperty(String propName)
    {
        if (completed)
        {
            if (propName.equals(Sasl.QOP))
            {
                return "auth";
            }
            else
            {
                return null;
            }
        }
        else
        {
            throw new IllegalStateException("CRAM-MD5 authentication not completed");
        }
    }

    /**
     * Disposes of any system resources or security-sensitive information the SaslClient might be using. Invoking this
     * method invalidates the SaslClient instance. This method is idempotent.
     *
     * @throws javax.security.sasl.SaslException If a problem was encountered while disposing the resources.
     */
    public void dispose() throws SaslException
    { }

    /*
     * Hashes its input arguments according to HMAC-MD5 (RFC 2104) and returns the resulting digest in its ASCII
     * representation.
     *
     * <p/> The HMAC-MD5 function is described as follows:
     * <p/><pre>
     *     MD5(key XOR opad, MD5(key XOR ipad, text))
     * </pre>
     *
     * <p/>Where key is an n byte key, ipad is the byte 0x36 repeated 64 times, opad is the byte 0x5c repeated 64 times
     * and text is the data to be protected.
     *
     * @param key  The key to hash by.
     * @param text The plain text to hash.
     *
     * @return The hashed text.
     *
     * @throws NoSuchAlgorithmException If the Java platform does not supply an MD5 implementation.
     */
    private static final String hmac_md5(byte[] key, byte[] text) throws NoSuchAlgorithmException
    {
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        /* digest the key if longer than 64 bytes */
        if (key.length > 64)
        {
            key = md5.digest(key);
        }

        byte[] ipad = new byte[MD5_BLOCKSIZE]; /* inner padding */
        byte[] opad = new byte[MD5_BLOCKSIZE]; /* outer padding */
        byte[] digest;
        int i;

        /* store key in pads */
        for (i = 0; i < MD5_BLOCKSIZE; i++)
        {
            for (; i < key.length; i++)
            {
                ipad[i] = key[i];
                opad[i] = key[i];
            }

            ipad[i] = 0x00;
            opad[i] = 0x00;
        }

        /* XOR key with pads */
        for (i = 0; i < MD5_BLOCKSIZE; i++)
        {
            ipad[i] ^= 0x36;
            opad[i] ^= 0x5c;
        }

        /* inner MD5 */
        md5.update(ipad);
        md5.update(text);
        digest = md5.digest();

        /* outer MD5 */
        md5.update(opad);
        md5.update(digest);
        digest = md5.digest();

        // Get character representation of digest
        StringBuffer digestString = new StringBuffer();

        for (i = 0; i < digest.length; i++)
        {
            if ((digest[i] & 0x000000ff) < 0x10)
            {
                digestString.append("0" + Integer.toHexString(digest[i] & 0x000000ff));
            }
            else
            {
                digestString.append(Integer.toHexString(digest[i] & 0x000000ff));
            }
        }

        return (digestString.toString());
    }

    /**
     * Overwrites the password with zeros.
     */
    private void clearPassword()
    {
        if (password != null)
        {
            // Zero out password.
            for (int i = 0; i < password.length; i++)
            {
                password[i] = (byte) 0;
            }

            password = null;
        }
    }
}
