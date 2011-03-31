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
package org.apache.qpid.tools;

import java.io.Console;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Command line tool to determine if a password will be affected by QPID-3158.
 */
public class QpidPasswordCheck
{
    
    public static void main(String[] argv) throws Exception
    {
        Console console = System.console();
        
        if (console == null) {
            System.err.println("Console not available.  This utility must be run on the command line.");
            System.exit(-1);
        }
        else 
        {
            System.err.println("Defect QPID-3158 in the Qpid java broker (releases 2.6.0.8 and below) means that certain valid client " +
            		"passwords cause spurious authentication failures when used from the Qpid 0-8 .NET client with a broker " +
                        "using the Base64MD5 password database type.");
            System.err.println();
            System.err.println("This utility accepts a candidate password and reports if the password will cause the defect.");
            System.err.println();
            
            final char[] password = console.readPassword("Enter candidate password: ");
            final char[] repeatedPassword = console.readPassword("Enter candidate password again: ");

            if (!Arrays.equals(password,repeatedPassword))
            {
                System.err.println("Sorry, those passwords do not match.");
                System.exit(-1);
            }
            
            
            final byte[] hashedPassword = MessageDigest.getInstance("MD5").digest(new String(password).getBytes());
            

            final char[] hashedPasswordChars = new char[hashedPassword.length];

            int index = 0;
            for (byte c : hashedPassword)
            {
                hashedPasswordChars[index++] = (char) c;
            }
            
            char[] brokenRepresentation = brokenToHex(hashedPasswordChars);
            char[] correctRepresentation = fixedToHex(hashedPasswordChars);
            
            if (Arrays.equals(brokenRepresentation,correctRepresentation))
            {
                System.err.println("Password is suitable for use.");
                System.exit(0);
            }
            else 
            {
                System.err.println("Sorry, that password is NOT suitable for use.");
                System.exit(1);
            }
            
        }    
    }
    
    
    private static char[] brokenToHex(char[] password)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : password)
        {
            //toHexString does not prepend 0 so we have to
            if (((byte) c > -1) && (byte) c < 10 )
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

    private static char[] fixedToHex(char[] password)
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

}
