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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.qpid.test.utils.QpidTestCase;

public class AESKeyFileEncrypterTest extends QpidTestCase
{
    private final SecureRandom _random = new SecureRandom();
    public static final String PLAINTEXT = "notaverygoodpassword";

    public void testSimpleEncryptDecrypt() throws Exception
    {
        doTestSimpleEncryptDecrypt(PLAINTEXT);
    }


    public void testRepeatedEncryptionsReturnDifferentValues()
    {
        SecretKeySpec secretKey = createSecretKey();
        AESKeyFileEncrypter encrypter = new AESKeyFileEncrypter(secretKey);

        Set<String> encryptions = new HashSet<>();

        int iterations = 100;

        for(int i = 0; i < iterations; i++)
        {
            encryptions.add(encrypter.encrypt(PLAINTEXT));
        }

        assertEquals("Not all encryptions were distinct", iterations, encryptions.size());

        for(String encrypted : encryptions)
        {
            assertEquals("Not all encryptions decrypt correctly", PLAINTEXT, encrypter.decrypt(encrypted));
        }
    }

    public void testCreationFailsOnInvalidSecret() throws Exception
    {
        try
        {
            new AESKeyFileEncrypter(null);
            fail("An encrypter should not be creatable from a null key");
        }
        catch(NullPointerException e)
        {
            // pass
        }

        try
        {
            PBEKeySpec keySpec = new PBEKeySpec("password".toCharArray());
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            new AESKeyFileEncrypter(factory.generateSecret(keySpec));
            fail("An encrypter should not be creatable from the wrong type of secret key");
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
    }

    public void testEncryptionOfEmptyString()
    {
        String text = "";
        doTestSimpleEncryptDecrypt(text);
    }

    private void doTestSimpleEncryptDecrypt(final String text)
    {
        SecretKeySpec secretKey = createSecretKey();
        AESKeyFileEncrypter encrypter = new AESKeyFileEncrypter(secretKey);

        String encrypted = encrypter.encrypt(text);
        assertNotNull("Encrypter did not return a result from encryption", encrypted);
        assertFalse("Plain text and encrypted version are equal", text.equals(encrypted));
        String decrypted = encrypter.decrypt(encrypted);
        assertNotNull("Encrypter did not return a result from decryption",decrypted);
        assertTrue("Encryption was not reversible", text.equals(decrypted));
    }

    public void testEncryptingNullFails()
    {
        try
        {
            SecretKeySpec secretKey = createSecretKey();
            AESKeyFileEncrypter encrypter = new AESKeyFileEncrypter(secretKey);

            String encrypted = encrypter.encrypt(null);
            fail("Attempting to encrypt null should fail");
        }
        catch(NullPointerException e)
        {
            // pass
        }
    }

    public void testEncryptingVeryLargeSecret()
    {
        Random random = new Random();
        byte[] data = new byte[4096];
        random.nextBytes(data);
        for(int i = 0; i < data.length; i++)
        {
            data[i] = (byte)(data[i] & 0xEF);
        }
        doTestSimpleEncryptDecrypt(new String(data, StandardCharsets.US_ASCII));
    }

    public void testDecryptNonsense()
    {

        SecretKeySpec secretKey = createSecretKey();
        AESKeyFileEncrypter encrypter = new AESKeyFileEncrypter(secretKey);


        try
        {
            encrypter.decrypt(null);
            fail("Should not decrypt a null value");
        }
        catch(NullPointerException e)
        {
            // pass
        }

        try
        {
            encrypter.decrypt("");
            fail("Should not decrypt the empty String");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }

        try
        {
            encrypter.decrypt("thisisnonsense");
            fail("Should not decrypt a small amount of nonsense");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }

        try
        {
            String answer = encrypter.decrypt("thisisn'tvalidBase64!soitshouldfailwithanIllegalArgumentException");
            fail("Should not decrypt a larger amount of nonsense");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }
    }

    private SecretKeySpec createSecretKey()
    {
        final byte[] keyData = new byte[32];
        _random.nextBytes(keyData);
        return new SecretKeySpec(keyData, "AES");
    }
}
