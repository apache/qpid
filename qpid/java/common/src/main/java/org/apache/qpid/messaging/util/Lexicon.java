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
import java.util.regex.Pattern;


/**
 * Lexicon
 *
 */

public class Lexicon
{

    private List<Token.Type> types;
    private Token.Type eof;

    public Lexicon()
    {
        this.types = new ArrayList<Token.Type>();
        this.eof = null;
    }

    public Token.Type define(String name, String pattern)
    {
        Token.Type t = new Token.Type(name, pattern);
        types.add(t);
        return t;
    }

    public Token.Type eof(String name)
    {
        Token.Type t = new Token.Type(name, null);
        eof = t;
        return t;
    }

    public Lexer compile()
    {
        StringBuilder joined = new StringBuilder();
        for (Token.Type t : types)
        {
            if (joined.length() > 0)
            {
                joined.append('|');
            }
            joined.append('(').append(t.getPattern()).append(')');
        }
        Pattern rexp = Pattern.compile(joined.toString());
        return new Lexer(new ArrayList<Token.Type>(types), eof, rexp);
    }

    public static final void main(String[] args)
    {
        StringBuilder input = new StringBuilder();
        for (String a : args)
        {
            if (input.length() > 0)
            {
                input.append(" ");
            }

            input.append(a);
        }

        Lexicon lxi = new Lexicon();
        lxi.define("FOR", "for");
        lxi.define("IF", "if");
        lxi.define("LPAREN", "\\(");
        lxi.define("RPAREN", "\\)");
        lxi.define("ID", "[\\S]+");
        lxi.define("WSPACE", "[\\s]+");
        lxi.eof("EOF");
        Lexer lx = lxi.compile();

        for (Token t : lx.lex(input.toString()))
        {
            System.out.println(t);
        }
    }

}
