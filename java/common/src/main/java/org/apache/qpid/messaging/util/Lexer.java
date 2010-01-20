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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Lexer
 *
 */

public class Lexer
{

    private List<Token.Type> types;
    private Token.Type eof;
    private Pattern rexp;

    public Lexer(List<Token.Type> types, Token.Type eof, Pattern rexp)
    {
        this.types = types;
        this.eof = eof;
        this.rexp = rexp;
    }

    public List<Token> lex(final String st)
    {
        List<Token> tokens = new ArrayList<Token>();

        int position = 0;
        Matcher m = rexp.matcher(st);
        OUTER: while (position < st.length())
        {
            m.region(position, st.length());
            if (m.lookingAt())
            {
                for (int i = 1; i <= m.groupCount(); i++)
                {
                    String value = m.group(i);
                    if (value != null)
                    {
                        tokens.add(new Token(types.get(i-1), value, st, m.start(i)));
                        position = m.end(i);
                        continue OUTER;
                    }
                }
                throw new RuntimeException("no group matched");
            }
            else
            {
                throw new LexError("unrecognized characters line:" + LineInfo.get(st, position));
            }
        }

        tokens.add(new Token(eof, null, st, position));

        return tokens;
    }

}
