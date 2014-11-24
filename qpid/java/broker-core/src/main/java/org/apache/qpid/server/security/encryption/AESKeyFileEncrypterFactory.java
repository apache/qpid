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
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
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
            checkFilePermissions(fileLocation, file);
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

    private void checkFilePermissions(String fileLocation, File file) throws IOException
    {
        if(isPosixFileSystem(file))
        {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file.toPath());

            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new IllegalArgumentException("Key file '"
                        + fileLocation
                        + "' has incorrect permissions.  Only the owner "
                        + "should be able to read or write this file.");
            }
        }
        else if(isAclFileSystem(file))
        {
            AclFileAttributeView attributeView = Files.getFileAttributeView(file.toPath(), AclFileAttributeView.class);
            ArrayList<AclEntry> acls = new ArrayList<>(attributeView.getAcl());
            ListIterator<AclEntry> iter = acls.listIterator();
            UserPrincipal owner = Files.getOwner(file.toPath());
            while(iter.hasNext())
            {
                AclEntry acl = iter.next();
                if(acl.type() == AclEntryType.ALLOW)
                {
                    Set<AclEntryPermission> originalPermissions = acl.permissions();
                    Set<AclEntryPermission> updatedPermissions = EnumSet.copyOf(originalPermissions);


                    if (updatedPermissions.removeAll(EnumSet.of(AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.EXECUTE,
                            AclEntryPermission.WRITE_ACL,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.WRITE_OWNER))) {
                        throw new IllegalArgumentException("Key file '"
                                + fileLocation
                                + "' has incorrect permissions.  The file should not be modifiable by any user.");
                    }
                    if (!owner.equals(acl.principal()) && updatedPermissions.removeAll(EnumSet.of(AclEntryPermission.READ_DATA))) {
                        throw new IllegalArgumentException("Key file '"
                                + fileLocation
                                + "' has incorrect permissions.  Only the owner should be able to read from the file.");
                    }
                }
            }
        }
        else
        {
            throw new IllegalArgumentException("Unable to determine a mechanism to protect access to the key file on this filesystem");
        }
    }

    private boolean isPosixFileSystem(File file) throws IOException
    {
        return Files.getFileStore(file.toPath().getRoot()).supportsFileAttributeView(PosixFileAttributeView.class);
    }

    private boolean isAclFileSystem(File file) throws IOException
    {
        return Files.getFileStore(file.toPath().getRoot()).supportsFileAttributeView(AclFileAttributeView.class);
    }


    private void createAndPopulateKeyFile(final File file)
    {
        try
        {
            createEmptyKeyFile(file);

            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE_BITS);
            SecretKey key = keyGenerator.generateKey();
            try(FileOutputStream os = new FileOutputStream(file))
            {
                os.write(key.getEncoded());
            }

            makeKeyFileReadOnly(file);
        }
        catch (NoSuchAlgorithmException | IOException e)
        {
            throw new IllegalArgumentException("Cannot create key file: " + e.getMessage(), e);
        }

    }

    private void makeKeyFileReadOnly(File file) throws IOException
    {
        if(isPosixFileSystem(file))
        {
            Files.setPosixFilePermissions(file.toPath(), EnumSet.of(PosixFilePermission.OWNER_READ));
        }
        else if(isAclFileSystem(file))
        {
            AclFileAttributeView attributeView = Files.getFileAttributeView(file.toPath(), AclFileAttributeView.class);
            ArrayList<AclEntry> acls = new ArrayList<>(attributeView.getAcl());
            ListIterator<AclEntry> iter = acls.listIterator();
            file.setReadOnly();
            while(iter.hasNext())
            {
                AclEntry acl = iter.next();
                Set<AclEntryPermission> originalPermissions = acl.permissions();
                Set<AclEntryPermission> updatedPermissions = EnumSet.copyOf(originalPermissions);

                if(updatedPermissions.removeAll(EnumSet.of(AclEntryPermission.APPEND_DATA,
                                                           AclEntryPermission.DELETE,
                                                           AclEntryPermission.EXECUTE,
                                                           AclEntryPermission.WRITE_ACL,
                                                           AclEntryPermission.WRITE_DATA,
                                                           AclEntryPermission.WRITE_ATTRIBUTES,
                                                           AclEntryPermission.WRITE_NAMED_ATTRS,
                                                           AclEntryPermission.WRITE_OWNER)))
                {
                    AclEntry.Builder builder = AclEntry.newBuilder(acl);
                    builder.setPermissions(updatedPermissions);
                    iter.set(builder.build());
                }
            }
            attributeView.setAcl(acls);
        }
        else
        {
            throw new IllegalArgumentException("Unable to determine a mechanism to protect access to the key file on this filesystem");
        }
    }

    private void createEmptyKeyFile(File file) throws IOException
    {
        final Path parentFilePath = file.getParentFile().toPath();

        if(isPosixFileSystem(file)) {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.createDirectories(parentFilePath, PosixFilePermissions.asFileAttribute(ownerOnly));

            Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        }
        else if(isAclFileSystem(file))
        {
            Files.createDirectories(parentFilePath);
            final UserPrincipal owner = Files.getOwner(parentFilePath);
            AclFileAttributeView attributeView = Files.getFileAttributeView(parentFilePath, AclFileAttributeView.class);
            List<AclEntry> acls = new ArrayList<>(attributeView.getAcl());
            ListIterator<AclEntry> iter = acls.listIterator();
            boolean found = false;
            while(iter.hasNext())
            {
                AclEntry acl = iter.next();
                if(!owner.equals(acl.principal()))
                {
                    iter.remove();
                }
                else if(acl.type() == AclEntryType.ALLOW)
                {
                    found = true;
                    AclEntry.Builder builder = AclEntry.newBuilder(acl);
                    Set<AclEntryPermission> permissions = EnumSet.copyOf(acl.permissions());
                    permissions.addAll(Arrays.asList(AclEntryPermission.ADD_FILE, AclEntryPermission.ADD_SUBDIRECTORY, AclEntryPermission.LIST_DIRECTORY));
                    builder.setPermissions(permissions);
                    iter.set(builder.build());
                }
            }
            if(!found)
            {
                AclEntry.Builder builder = AclEntry.newBuilder();
                builder.setPermissions(AclEntryPermission.ADD_FILE, AclEntryPermission.ADD_SUBDIRECTORY, AclEntryPermission.LIST_DIRECTORY);
                builder.setType(AclEntryType.ALLOW);
                builder.setPrincipal(owner);
                acls.add(builder.build());
            }
            attributeView.setAcl(acls);

            Files.createFile(file.toPath(), new FileAttribute<List<AclEntry>>()
            {
                @Override
                public String name()
                {
                    return "acl:acl";
                }

                @Override
                public List<AclEntry> value() {
                    AclEntry.Builder builder = AclEntry.newBuilder();
                    builder.setType(AclEntryType.ALLOW);
                    builder.setPermissions(AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.READ_ACL,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.READ_NAMED_ATTRS,
                            AclEntryPermission.WRITE_ACL,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.WRITE_DATA);
                    builder.setPrincipal(owner);
                    return Collections.singletonList(builder.build());
                }
            });

        }
        else
        {
            throw new IllegalArgumentException("Unable to determine a mechanism to protect access to the key file on this filesystem");
        }
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
