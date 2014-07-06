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
package org.apache.qpid.client.security.scram;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;

public abstract class AbstractScramSaslClient implements SaslClient
{

    private static final byte[] INT_1 = new byte[]{0, 0, 0, 1};
    private static final String GS2_HEADER = "n,,";
    private static final Charset ASCII = Charset.forName("ASCII");

    private final String _digestName;
    private final String _hmacName;

    private String _username;
    private final String _clientNonce = UUID.randomUUID().toString();
    private String _serverNonce;
    private byte[] _salt;
    private int _iterationCount;
    private String _clientFirstMessageBare;
    private byte[] _serverSignature;

    enum State
    {
        INITIAL,
        CLIENT_FIRST_SENT,
        CLIENT_PROOF_SENT,
        COMPLETE
    }

    public final String _mechanism;

    private final CallbackHandler _callbackHandler;

    private State _state = State.INITIAL;

    public AbstractScramSaslClient(final CallbackHandler cbh,
                                   final String mechanism,
                                   final String digestName,
                                   final String hmacName)
    {
        _callbackHandler = cbh;
        _mechanism = mechanism;
        _digestName = digestName;
        _hmacName = hmacName;

    }

    @Override
    public String getMechanismName()
    {
        return _mechanism;
    }

    @Override
    public boolean hasInitialResponse()
    {
        return true;
    }

    @Override
    public byte[] evaluateChallenge(final byte[] challenge) throws SaslException
    {
        byte[] response;
        switch(_state)
        {
            case INITIAL:
                response = initialResponse();
                _state = State.CLIENT_FIRST_SENT;
                break;
            case CLIENT_FIRST_SENT:
                response = calculateClientProof(challenge);
                _state = State.CLIENT_PROOF_SENT;
                break;
            case CLIENT_PROOF_SENT:
                evaluateOutcome(challenge);
                response = null;
                _state = State.COMPLETE;
                break;
            default:
                throw new SaslException("No challenge expected in state " + _state);
        }
        return response;
    }

    private void evaluateOutcome(final byte[] challenge) throws SaslException
    {
        String serverFinalMessage = new String(challenge, ASCII);
        String[] parts = serverFinalMessage.split(",");
        if(!parts[0].startsWith("v="))
        {
            throw new SaslException("Server final message did not contain verifier");
        }
        byte[] serverSignature = DatatypeConverter.parseBase64Binary(parts[0].substring(2));
        if(!Arrays.equals(_serverSignature, serverSignature))
        {
            throw new SaslException("Server signature did not match");
        }
    }

    private byte[] calculateClientProof(final byte[] challenge) throws SaslException
    {
        try
        {
            String serverFirstMessage = new String(challenge, ASCII);
            String[] parts = serverFirstMessage.split(",");
            if(parts.length < 3)
            {
                throw new SaslException("Server challenge '" + serverFirstMessage + "' cannot be parsed");
            }
            else if(parts[0].startsWith("m="))
            {
                throw new SaslException("Server requires mandatory extension which is not supported: " + parts[0]);
            }
            else if(!parts[0].startsWith("r="))
            {
                throw new SaslException("Server challenge '" + serverFirstMessage + "' cannot be parsed, cannot find nonce");
            }
            String nonce = parts[0].substring(2);
            if(!nonce.startsWith(_clientNonce))
            {
                throw new SaslException("Server challenge did not use correct client nonce");
            }
            _serverNonce = nonce;
            if(!parts[1].startsWith("s="))
            {
                throw new SaslException("Server challenge '" + serverFirstMessage + "' cannot be parsed, cannot find salt");
            }
            String base64Salt = parts[1].substring(2);
            _salt = DatatypeConverter.parseBase64Binary(base64Salt);
            if(!parts[2].startsWith("i="))
            {
                throw new SaslException("Server challenge '" + serverFirstMessage + "' cannot be parsed, cannot find iteration count");
            }
            String iterCountString = parts[2].substring(2);
            _iterationCount = Integer.parseInt(iterCountString);
            if(_iterationCount <= 0)
            {
                throw new SaslException("Iteration count " + _iterationCount + " is not a positive integer");
            }
            PasswordCallback passwordCallback = new PasswordCallback("Password", false);
            _callbackHandler.handle(new Callback[] { passwordCallback });
            byte[] passwordBytes = saslPrep(new String(passwordCallback.getPassword())).getBytes("UTF-8");

            byte[] saltedPassword = generateSaltedPassword(passwordBytes);


            String clientFinalMessageWithoutProof =
                    "c=" + DatatypeConverter.printBase64Binary(GS2_HEADER.getBytes(ASCII))
                    + ",r=" + _serverNonce;

            String authMessage = _clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;

            byte[] clientKey = computeHmac(saltedPassword, "Client Key");
            byte[] storedKey = MessageDigest.getInstance(_digestName).digest(clientKey);

            byte[] clientSignature = computeHmac(storedKey, authMessage);

            byte[] clientProof = clientKey.clone();
            for(int i = 0 ; i < clientProof.length; i++)
            {
                clientProof[i] ^= clientSignature[i];
            }
            byte[] serverKey = computeHmac(saltedPassword, "Server Key");
            _serverSignature = computeHmac(serverKey, authMessage);

            String finalMessageWithProof = clientFinalMessageWithoutProof
                                           + ",p=" + DatatypeConverter.printBase64Binary(clientProof);
            return finalMessageWithProof.getBytes();
        }
        catch (UnsupportedEncodingException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (IllegalArgumentException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (UnsupportedCallbackException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
    }

    private byte[] computeHmac(final byte[] key, final String string)
            throws SaslException, UnsupportedEncodingException
    {
        Mac mac = createHmac(key);
        mac.update(string.getBytes(ASCII));
        return mac.doFinal();
    }

    private byte[] generateSaltedPassword(final byte[] passwordBytes) throws SaslException
    {
        Mac mac = createHmac(passwordBytes);

        mac.update(_salt);
        mac.update(INT_1);
        byte[] result = mac.doFinal();

        byte[] previous = null;
        for(int i = 1; i < _iterationCount; i++)
        {
            mac.update(previous != null? previous: result);
            previous = mac.doFinal();
            for(int x = 0; x < result.length; x++)
            {
                result[x] ^= previous[x];
            }
        }

        return result;
    }

    private Mac createHmac(final byte[] keyBytes)
            throws SaslException
    {
        try
        {
            SecretKeySpec key = new SecretKeySpec(keyBytes, _hmacName);
            Mac mac = Mac.getInstance(_hmacName);
            mac.init(key);
            return mac;
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (InvalidKeyException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
    }


    private byte[] initialResponse() throws SaslException
    {
        try
        {
            StringBuffer buf = new StringBuffer("n=");
            NameCallback nameCallback = new NameCallback("Username?");
            _callbackHandler.handle(new Callback[] { nameCallback });
            _username = nameCallback.getName();
            buf.append(saslPrep(_username));
            buf.append(",r=");
            buf.append(_clientNonce);
            _clientFirstMessageBare = buf.toString();
            return (GS2_HEADER + _clientFirstMessageBare).getBytes(ASCII);
        }
        catch (UnsupportedCallbackException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
    }

    private String saslPrep(String name) throws SaslException
    {
        // TODO - a real implementation of SaslPrep

        if(!ASCII.newEncoder().canEncode(name))
        {
            throw new SaslException("Can only encode names and passwords which are restricted to ASCII characters");
        }

        name = name.replace("=", "=3D");
        name = name.replace(",", "=2C");
        return name;
    }

    @Override
    public boolean isComplete()
    {
        return _state == State.COMPLETE;
    }

    @Override
    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException
    {
        throw new IllegalStateException("No security layer supported");
    }

    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException
    {
        throw new IllegalStateException("No security layer supported");
    }

    @Override
    public Object getNegotiatedProperty(final String propName)
    {
        return null;
    }

    @Override
    public void dispose() throws SaslException
    {

    }

}
