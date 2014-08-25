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
package org.apache.qpid.server.security.encryption;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.server.configuration.IllegalConfigurationException;

class AESKeyFileEncrypter implements ConfigurationSecretEncrypter
{
    private static final String CIPHER_NAME = "AES/CBC/PKCS5Padding";
    private static final int AES_INITIALIZATION_VECTOR_LENGTH = 16;
    private final SecretKey _secretKey;
    private final SecureRandom _random = new SecureRandom();

    AESKeyFileEncrypter(SecretKey secretKey)
    {
        _secretKey = secretKey;
    }

    @Override
    public String encrypt(final String unencrypted)
    {
        byte[] unencryptedBytes = unencrypted.getBytes(StandardCharsets.UTF_8);
        try
        {
            byte[] ivbytes = new byte[AES_INITIALIZATION_VECTOR_LENGTH];
            _random.nextBytes(ivbytes);
            Cipher cipher = Cipher.getInstance(CIPHER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, _secretKey, new IvParameterSpec(ivbytes));
            byte[] encryptedBytes = readFromCipherStream(unencryptedBytes, cipher);
            byte[] output = new byte[AES_INITIALIZATION_VECTOR_LENGTH + encryptedBytes.length];
            System.arraycopy(ivbytes, 0, output, 0, AES_INITIALIZATION_VECTOR_LENGTH);
            System.arraycopy(encryptedBytes, 0, output, AES_INITIALIZATION_VECTOR_LENGTH, encryptedBytes.length);
            return DatatypeConverter.printBase64Binary(output);
        }
        catch (IOException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e)
        {
            throw new IllegalConfigurationException("Unable to encrypt secret", e);
        }
    }

    @Override
    public String decrypt(final String encrypted)
    {
        byte[] encryptedBytes = DatatypeConverter.parseBase64Binary(encrypted);
        try
        {
            Cipher cipher = Cipher.getInstance(CIPHER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, _secretKey, new IvParameterSpec(encryptedBytes, 0,
                                                                             AES_INITIALIZATION_VECTOR_LENGTH));
            return new String(readFromCipherStream(encryptedBytes,
                                                   AES_INITIALIZATION_VECTOR_LENGTH,
                                                   encryptedBytes.length - AES_INITIALIZATION_VECTOR_LENGTH,
                                                   cipher), StandardCharsets.UTF_8);
        }
        catch (IOException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e)
        {
            throw new IllegalConfigurationException("Unable to encrypt secret", e);
        }
    }


    private byte[] readFromCipherStream(final byte[] unencryptedBytes, final Cipher cipher) throws IOException
    {
        return readFromCipherStream(unencryptedBytes, 0, unencryptedBytes.length, cipher);
    }

    private byte[] readFromCipherStream(final byte[] unencryptedBytes, int offset, int length, final Cipher cipher)
            throws IOException
    {
        final byte[] encryptedBytes;
        try (CipherInputStream cipherInputStream = new CipherInputStream(new ByteArrayInputStream(unencryptedBytes,
                                                                                                  offset,
                                                                                                  length), cipher))
        {
            byte[] buf = new byte[1024];
            int pos = 0;
            int read;
            while ((read = cipherInputStream.read(buf, pos, buf.length - pos)) != -1)
            {
                pos += read;
                if (pos == buf.length - 1)
                {
                    byte[] tmp = buf;
                    buf = new byte[buf.length + 1024];
                    System.arraycopy(tmp, 0, buf, 0, tmp.length);
                }
            }
            encryptedBytes = new byte[pos];
            System.arraycopy(buf, 0, encryptedBytes, 0, pos);
        }
        return encryptedBytes;
    }



}
