/*
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
 */
package org.apache.qpid.systest.management.jmx;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.server.security.auth.manager.Base64MD5PasswordDatabaseAuthenticationManager;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class UserManagementWithBase64MD5PasswordsTest extends UserManagementTest
{
    @Override
    protected void writeUsernamePassword(final FileWriter writer, final String username, final String password)
            throws IOException
    {
        writer.append(username);
        writer.append(":");
        byte[] data = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest md = null;
        try
        {
            md = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServerScopedRuntimeException("MD5 not supported although Java compliance requires it");
        }

        md.update(data);
        writer.append(DatatypeConverter.printBase64Binary(md.digest()));
        writer.append('\n');
    }


    @Override
    protected String getAuthenticationManagerType()
    {
        return Base64MD5PasswordDatabaseAuthenticationManager.PROVIDER_TYPE;
    }
}
