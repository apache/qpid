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

import java.util.ArrayList;
import java.util.List;


/**
 * Parser
 *
 */

class Parser
{

    private List<Token> tokens;
    private int idx = 0;

    Parser(List<Token> tokens)
    {
        this.tokens = tokens;
        this.idx = 0;
    }

    Token next()
    {
        return tokens.get(idx);
    }

    boolean matches(Token.Type ... types)
    {
        for (Token.Type t : types)
        {
            if (next().getType() == t)
            {
                return true;
            }
        }
        return false;
    }

    Token eat(Token.Type ... types)
    {
        if (types.length > 0 && !matches(types))
        {
            throw new ParseError(next(), types);
        }
        else
        {
            Token t = next();
            idx += 1;
            return t;
        }
    }

    List<Token> eat_until(Token.Type ... types)
    {
        List<Token> result = new ArrayList();
        while (!matches(types))
        {
            result.add(eat());
        }
        return result;
    }

}
