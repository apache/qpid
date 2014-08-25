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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.ConfigurationSecretEncrypterFactory;
import org.apache.qpid.server.plugin.PluggableService;

@PluggableService
public class AESKeyFileEncrypterFactory implements ConfigurationSecretEncrypterFactory
{
    static final String ENCRYPTER_KEY_FILE = "encrypter.key.file";

    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int AES_KEY_SIZE_BYTES = AES_KEY_SIZE_BITS / 8;
    private static final String AES_ALGORITHM = "AES";

    public static final String TYPE = "AESKeyFile";

    static final String DEFAULT_KEYS_SUBDIR_NAME = ".keys";

    @Override
    public ConfigurationSecretEncrypter createEncrypter(final ConfiguredObject<?> object)
    {
        String fileLocation;
        if(object.getContextKeys(false).contains(ENCRYPTER_KEY_FILE))
        {
            fileLocation = object.getContextValue(String.class, ENCRYPTER_KEY_FILE);
        }
        else
        {

            fileLocation = object.getContextValue(String.class, BrokerOptions.QPID_WORK_DIR)
                           + File.separator + DEFAULT_KEYS_SUBDIR_NAME + File.separator
                           + object.getCategoryClass().getSimpleName() + "_"
                           + object.getName() + ".key";

            Map<String, String> context = object.getContext();
            Map<String, String> modifiedContext = new LinkedHashMap<>(context);
            modifiedContext.put(ENCRYPTER_KEY_FILE, fileLocation);

            object.setAttribute(ConfiguredObject.CONTEXT, context, modifiedContext);
        }
        File file = new File(fileLocation);
        if(!file.exists())
        {
            createAndPopulateKeyFile(file);
        }
        if(!file.isFile())
        {
            throw new IllegalArgumentException("File '"+fileLocation+"' is not a regular file.");
        }
        try
        {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file.toPath());

            if (permissions.contains(PosixFilePermission.GROUP_READ)
                || permissions.contains(PosixFilePermission.OTHERS_READ)
                || permissions.contains(PosixFilePermission.GROUP_WRITE)
                || permissions.contains(PosixFilePermission.OTHERS_WRITE))
            {
                throw new IllegalArgumentException("Key file '"
                                                + fileLocation
                                                + "' has incorrect permissions.  Only the owner "
                                                + "should be able to read or write this file.");
            }
            if(Files.size(file.toPath()) != AES_KEY_SIZE_BYTES)
            {
                throw new IllegalArgumentException("Key file '" + fileLocation + "' contains an incorrect about of data");
            }

            try(FileInputStream inputStream = new FileInputStream(file))
            {
                byte[] key = new byte[AES_KEY_SIZE_BYTES];
                int pos = 0;
                int read;
                while(pos < key.length && -1 != ( read = inputStream.read(key, pos, key.length - pos)))
                {
                    pos += read;
                }
                if(pos != key.length)
                {
                    throw new IllegalConfigurationException("Key file '" + fileLocation + "' contained an incorrect about of data");
                }
                SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGORITHM);
                return new AESKeyFileEncrypter(keySpec);
            }
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Unable to get file permissions: " + e.getMessage(), e);
        }
    }

    private void createAndPopulateKeyFile(final File file)
    {
        try
        {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ,
                                                            PosixFilePermission.OWNER_WRITE,
                                                            PosixFilePermission.OWNER_EXECUTE);
            Files.createDirectories(file.getParentFile().toPath(), PosixFilePermissions.asFileAttribute(ownerOnly));

            Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));

            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE_BITS);
            SecretKey key = keyGenerator.generateKey();
            try(FileOutputStream os = new FileOutputStream(file))
            {
                os.write(key.getEncoded());
            }

            Files.setPosixFilePermissions(file.toPath(), EnumSet.of(PosixFilePermission.OWNER_READ));
        }
        catch (NoSuchAlgorithmException | IOException e)
        {
            throw new IllegalArgumentException("Cannot create key file: " + e.getMessage(), e);
        }

    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
