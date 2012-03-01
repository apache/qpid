/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.tools.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Base64;

public class Passwd
{
    public static void main(String args[]) throws NoSuchAlgorithmException, DigestException, IOException
    {
        if (args.length != 2)
        {
            System.out.println("Passwd <username> <password>");
            System.exit(0);
        }

        Passwd passwd = new Passwd();
        String output = passwd.getOutput(args[0], args[1]);
        System.out.println(output);
    }

    public String getOutput(String userName, String password) throws UnsupportedEncodingException, NoSuchAlgorithmException
    {
        byte[] data = password.getBytes("utf-8");

        MessageDigest md = MessageDigest.getInstance("MD5");

        for (byte b : data)
        {
            md.update(b);
        }

        byte[] digest = md.digest();

        Base64 b64 = new Base64();

        byte[] encoded = b64.encode(digest);

        String encodedStr = new String(encoded, Charset.forName("utf-8"));
        return userName + ":" + encodedStr;
    }

}
