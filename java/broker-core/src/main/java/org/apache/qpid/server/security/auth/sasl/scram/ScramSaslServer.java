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
package org.apache.qpid.server.security.auth.sasl.scram;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.server.security.auth.manager.AbstractScramAuthenticationManager;

public class ScramSaslServer implements SaslServer
{
    public final String _mechanism;
    public final String _hmacName;
    public final String _digestName;

    private static final Charset ASCII = Charset.forName("ASCII");

    private final AbstractScramAuthenticationManager _authManager;
    private State _state = State.INITIAL;
    private String _nonce;
    private String _username;
    private byte[] _gs2Header;
    private String _serverFirstMessage;
    private String _clientFirstMessageBare;
    private byte[] _serverSignature;

    public ScramSaslServer(final AbstractScramAuthenticationManager authenticationManager,
                           final String mechanism,
                           final String hmacName,
                           final String digestName)
    {
        _authManager = authenticationManager;
        _mechanism = mechanism;
        _hmacName = hmacName;
        _digestName = digestName;
    }

    enum State
    {
        INITIAL,
        SERVER_FIRST_MESSAGE_SENT,
        COMPLETE
    }

    @Override
    public String getMechanismName()
    {
        return _mechanism;
    }

    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException
    {
        byte[] challenge;
        switch (_state)
        {
            case INITIAL:
                challenge = generateServerFirstMessage(response);
                _state = State.SERVER_FIRST_MESSAGE_SENT;
                break;
            case SERVER_FIRST_MESSAGE_SENT:
                challenge = generateServerFinalMessage(response);
                _state = State.COMPLETE;
                break;
            case COMPLETE:
                if(response == null || response.length == 0)
                {
                    challenge = new byte[0];
                    break;
                }
            default:
                throw new SaslException("No response expected in state " + _state);

        }
        return challenge;
    }

    private byte[] generateServerFirstMessage(final byte[] response) throws SaslException
    {
        String clientFirstMessage = new String(response, ASCII);
        if(!clientFirstMessage.startsWith("n"))
        {
            throw new SaslException("Cannot parse gs2-header");
        }
        String[] parts = clientFirstMessage.split(",");
        if(parts.length < 4)
        {
            throw new SaslException("Cannot parse client first message");
        }
        _gs2Header = ("n,"+parts[1]+",").getBytes(ASCII);
        _clientFirstMessageBare = clientFirstMessage.substring(_gs2Header.length);
        if(!parts[2].startsWith("n="))
        {
            throw new SaslException("Cannot parse client first message");
        }
        _username = decodeUsername(parts[2].substring(2));
        if(!parts[3].startsWith("r="))
        {
            throw new SaslException("Cannot parse client first message");
        }
        _nonce = parts[3].substring(2) + UUID.randomUUID().toString();

        int count = _authManager.getIterationCount();
        byte[] saltBytes = _authManager.getSalt(_username);
        _serverFirstMessage = "r="+_nonce+",s="+ DatatypeConverter.printBase64Binary(saltBytes)+",i=" + count;
        return _serverFirstMessage.getBytes(ASCII);
    }

    private String decodeUsername(String username) throws SaslException
    {
        if(username.contains("="))
        {
            String check = username;
            while (check.contains("="))
            {
                check = check.substring(check.indexOf('=') + 1);
                if (!(check.startsWith("2C") || check.startsWith("3D")))
                {
                    throw new SaslException("Invalid username");
                }
            }
            username = username.replace("=2C", ",");
            username = username.replace("=3D","=");
        }
        return username;
    }


    private byte[] generateServerFinalMessage(final byte[] response) throws SaslException
    {
        try
        {
            String clientFinalMessage = new String(response, ASCII);
            String[] parts = clientFinalMessage.split(",");
            if(!parts[0].startsWith("c="))
            {
                throw new SaslException("Cannot parse client final message");
            }
            if(!Arrays.equals(_gs2Header,DatatypeConverter.parseBase64Binary(parts[0].substring(2))))
            {
                throw new SaslException("Client final message channel bind data invalid");
            }
            if(!parts[1].startsWith("r="))
            {
                throw new SaslException("Cannot parse client final message");
            }
            if(!parts[1].substring(2).equals(_nonce))
            {
                throw new SaslException("Client final message has incorrect nonce value");
            }
            if(!parts[parts.length-1].startsWith("p="))
            {
                throw new SaslException("Client final message does not have proof");
            }

            String clientFinalMessageWithoutProof = clientFinalMessage.substring(0,clientFinalMessage.length()-(1+parts[parts.length-1].length()));
            byte[] proofBytes = DatatypeConverter.parseBase64Binary(parts[parts.length-1].substring(2));

            String authMessage = _clientFirstMessageBare + "," + _serverFirstMessage + "," + clientFinalMessageWithoutProof;

            byte[] saltedPassword = _authManager.getSaltedPassword(_username);

            byte[] clientKey = computeHmac(saltedPassword, "Client Key");

            byte[] storedKey = MessageDigest.getInstance(_digestName).digest(clientKey);

            byte[] clientSignature = computeHmac(storedKey, authMessage);

            byte[] clientProof = clientKey.clone();
            for(int i = 0 ; i < clientProof.length; i++)
            {
                clientProof[i] ^= clientSignature[i];
            }

            if(!Arrays.equals(clientProof, proofBytes))
            {
                throw new SaslException("Authentication failed");
            }
            byte[] serverKey = computeHmac(saltedPassword, "Server Key");
            String finalResponse = "v=" + DatatypeConverter.printBase64Binary(computeHmac(serverKey, authMessage));

            return finalResponse.getBytes(ASCII);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
    }

    @Override
    public boolean isComplete()
    {
        return _state == State.COMPLETE;
    }

    @Override
    public String getAuthorizationID()
    {
        return _username;
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

    private byte[] computeHmac(final byte[] key, final String string)
            throws SaslException, UnsupportedEncodingException
    {
        Mac mac = createSha1Hmac(key);
        mac.update(string.getBytes(ASCII));
        return mac.doFinal();
    }


    private Mac createSha1Hmac(final byte[] keyBytes)
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

}
