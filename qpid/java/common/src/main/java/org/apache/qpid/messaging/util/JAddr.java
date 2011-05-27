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
package org.apache.qpid.messaging.util;

import java.io.InputStreamReader;

import java.util.List;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.util.ParseError;
import org.apache.qpid.messaging.util.Token;

import static org.apache.qpid.messaging.util.PyPrint.pprint;


/**
 * JAddr
 *
 */

public class JAddr
{

    public static final void main(String[] args) throws Exception
    {
        StringBuilder addr = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(System.in);

        char[] buf = new char[1024];
        while (true)
        {
            int n = reader.read(buf, 0, buf.length);
            if (n < 0)
            {
                break;
            }
            addr.append(buf, 0, n);
        }

        if ("parse".equals(args[0]))
        {
            try
            {
                Address address = Address.parse(addr.toString());
                System.out.println(pprint_address(address));
            }
            catch (ParseError e)
            {
                System.out.println(String.format("ERROR: %s", e.getMessage()));
            }
        }
        else
        {
            List<Token> tokens = AddressParser.lex(addr.toString());
            for (Token t : tokens)
            {
                String value = t.getValue();
                if (value != null)
                {
                    value = value.replace("\\", "\\\\").replace("\n", "\\n");
                    System.out.println(String.format("%s:%s:%s", t.getType(), t.getPosition(), value));
                }
                else
                {
                    System.out.println(String.format("%s:%s", t.getType(), t.getPosition()));
                }
            }
        }
    }

    private static String pprint_address(Address addr)
    {
        return String.format("NAME: %s\nSUBJECT: %s\nOPTIONS: %s",
                             pprint(addr.getName()),
                             pprint(addr.getSubject()),
                             pprint(addr.getOptions()));
    }

}
