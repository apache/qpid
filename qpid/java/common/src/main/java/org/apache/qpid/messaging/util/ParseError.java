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

import org.apache.qpid.util.Strings;


/**
 * ParseError
 *
 */

public class ParseError extends RuntimeException
{

    private static String msg(Token token, Token.Type ... expected)
    {
        LineInfo li = token.getLineInfo();
        String exp = Strings.join(", ", expected);
        if (expected.length > 1)
        {
            exp = String.format("(%s)", exp);
        }

        if (expected.length > 0)
        {
            return String.format("expecting %s, got %s line:%s", exp, token, li);
        }
        else
        {
            return String.format("unexpected token %s line:%s", token, li);
        }
    }

    public ParseError(Token token, Token.Type ... expected)
    {
        super(msg(token, expected));
    }

}
