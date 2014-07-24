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
package org.apache.qpid.server.security.auth.sasl.crammd5;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePasswordInitialiser;

public class CRAMMD5HexInitialiser extends UsernamePasswordInitialiser
{
    public String getMechanismName()
    {
        return CRAMMD5HexSaslServer.MECHANISM;
    }

    public void initialise(PrincipalDatabase db)
    {
        super.initialise(new HexifyPrincipalDatabase(db));

    }

    private static class HexifyPrincipalDatabase implements PrincipalDatabase
    {
        private PrincipalDatabase _realPrincipalDatabase;

        HexifyPrincipalDatabase(PrincipalDatabase db)
        {
            _realPrincipalDatabase = db;
        }

        private char[] toHex(char[] password)
        {
            StringBuilder sb = new StringBuilder();
            for (char c : password)
            {
                //toHexString does not prepend 0 so we have to
                if (((byte) c > -1) && (byte) c < 0x10 )
                {
                    sb.append(0);
                }

                sb.append(Integer.toHexString(c & 0xFF));
            }

            //Extract the hex string as char[]
            char[] hex = new char[sb.length()];

            sb.getChars(0, sb.length(), hex, 0);

            return hex;
        }

        public void setPassword(Principal principal, PasswordCallback callback) throws IOException, AccountNotFoundException
        {
            //Let the read DB set the password
            _realPrincipalDatabase.setPassword(principal, callback);

            //Retrieve the setpassword
            char[] plainPassword = callback.getPassword();

            char[] hexPassword = toHex(plainPassword);

            callback.setPassword(hexPassword);
        }

        // Simply delegate to the real PrincipalDB
        public boolean verifyPassword(String principal, char[] password) throws AccountNotFoundException
        {
            return _realPrincipalDatabase.verifyPassword(principal, password);
        }

        public boolean updatePassword(Principal principal, char[] password) throws AccountNotFoundException
        {
            return _realPrincipalDatabase.updatePassword(principal, password);
        }

        public boolean createPrincipal(Principal principal, char[] password)
        {
            return _realPrincipalDatabase.createPrincipal(principal, password);
        }

        public boolean deletePrincipal(Principal principal) throws AccountNotFoundException
        {
            return _realPrincipalDatabase.deletePrincipal(principal);
        }

        public Principal getUser(String username)
        {
            return _realPrincipalDatabase.getUser(username);
        }

        public List<Principal> getUsers()
        {
            return _realPrincipalDatabase.getUsers();
        }
	
        public void reload() throws IOException
        {
            _realPrincipalDatabase.reload();
        }

        @Override
        public void open(File passwordFile) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getMechanisms()
        {
            return _realPrincipalDatabase.getMechanisms();
        }

        @Override
        public SaslServer createSaslServer(String mechanism, String localFQDN,
                Principal externalPrincipal) throws SaslException
        {
            return _realPrincipalDatabase.createSaslServer(mechanism, localFQDN, externalPrincipal);
        }
    }

}
