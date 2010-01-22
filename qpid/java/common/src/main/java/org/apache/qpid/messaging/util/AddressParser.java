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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.Address;


/**
 * AddressParser
 *
 */

public class AddressParser extends Parser
{

    private static Lexicon lxi = new Lexicon();

    private static Token.Type LBRACE = lxi.define("LBRACE", "\\{");
    private static Token.Type RBRACE = lxi.define("RBRACE", "\\}");
    private static Token.Type LBRACK = lxi.define("LBRACK", "\\[");
    private static Token.Type RBRACK = lxi.define("RBRACK", "\\]");
    private static Token.Type COLON = lxi.define("COLON", ":");
    private static Token.Type SEMI = lxi.define("SEMI", ";");
    private static Token.Type SLASH = lxi.define("SLASH", "/");
    private static Token.Type COMMA = lxi.define("COMMA", ",");
    private static Token.Type NUMBER = lxi.define("NUMBER", "[+-]?[0-9]*\\.?[0-9]+");
    private static Token.Type TRUE = lxi.define("TRUE", "True");
    private static Token.Type FALSE = lxi.define("FALSE", "False");
    private static Token.Type ID = lxi.define("ID", "[a-zA-Z_](?:[a-zA-Z0-9_-]*[a-zA-Z0-9_])?");
    private static Token.Type STRING = lxi.define("STRING", "\"(?:[^\\\"]|\\.)*\"|'(?:[^\\']|\\.)*'");
    private static Token.Type ESC = lxi.define("ESC", "\\\\[^ux]|\\\\x[0-9a-fA-F][0-9a-fA-F]|\\\\u[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]");
    private static Token.Type SYM = lxi.define("SYM", "[.#*%@$^!+-]");
    private static Token.Type WSPACE = lxi.define("WSPACE", "[\\s]+");
    private static Token.Type EOF = lxi.eof("EOF");

    private static Lexer LEXER = lxi.compile();

    public static List<Token> lex(String input)
    {
        return LEXER.lex(input);
    }

    static List<Token> wlex(String input)
    {
        List<Token> tokens = new ArrayList<Token>();
        for (Token t : lex(input))
        {
            if (t.getType() != WSPACE)
            {
                tokens.add(t);
            }
        }
        return tokens;
    }

    static String unquote(String st, Token tok)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < st.length() - 1; i++)
        {
            char ch = st.charAt(i);
            if (ch == '\\')
            {
                char code = st.charAt(i+1);
                switch (code)
                {
                case '\n':
                    break;
                case '\\':
                    result.append('\\');
                    break;
                case '\'':
                    result.append('\'');
                    break;
                case '"':
                    result.append('"');
                    break;
                case 'a':
                    result.append((char) 0x07);
                    break;
                case 'b':
                    result.append((char) 0x08);
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'u':
                    result.append(decode(st.substring(i+2, i+6)));
                    i += 4;
                    break;
                case 'v':
                    result.append((char) 0x0b);
                    break;
                case 'o':
                    result.append(decode(st.substring(i+2, i+4), 8));
                    i += 2;
                    break;
                case 'x':
                    result.append(decode(st.substring(i+2, i+4)));
                    i += 2;
                    break;
                default:
                    throw new ParseError(tok);
                }
                i += 1;
            }
            else
            {
                result.append(ch);
            }
        }

        return result.toString();
    }

    static char[] decode(String hex)
    {
        return decode(hex, 16);
    }

    static char[] decode(String code, int radix)
    {
        return Character.toChars(Integer.parseInt(code, radix));
    }

    static String tok2str(Token tok)
    {
        Token.Type type = tok.getType();
        String value = tok.getValue();

        if (type == STRING)
        {
            return unquote(value, tok);
        }
        else if (type == ESC)
        {
            if (value.charAt(1) == 'x' || value.charAt(1) == 'u')
            {
                return new String(decode(value.substring(2)));
            }
            else
            {
                return value.substring(1);
            }
        }
        else
        {
            return value;
        }
    }

    static Object tok2obj(Token tok)
    {
        Token.Type type = tok.getType();
        String value = tok.getValue();
        if (type == STRING)
        {
            return unquote(value, tok);
        }
        else if (type == NUMBER)
        {
            if (value.indexOf('.') >= 0)
            {
                return Double.valueOf(value);
            }
            else
            {
                return Integer.decode(value);
            }
        }
        else if (type == TRUE)
        {
            return true;
        }
        else if (type == FALSE)
        {
            return false;
        }
        else
        {
            return value;
        }
    }

    static String toks2str(List<Token> toks)
    {
        if (toks.size() > 0)
        {
            StringBuilder result = new StringBuilder();
            for (Token t : toks)
            {
                result.append(tok2str(t));
            }
            return result.toString();
        }
        else
        {
            return null;
        }
    }

    public AddressParser(String input)
    {
        super(wlex(input));
    }

    public Address parse()
    {
        Address result = address();
        eat(EOF);
        return result;
    }

    public Address address()
    {
        String name = toks2str(eat_until(SLASH, SEMI, EOF));

        if (name == null)
        {
            throw new ParseError(next());
        }

        String subject;
        if (matches(SLASH))
        {
            eat(SLASH);
            subject = toks2str(eat_until(SEMI, EOF));
        }
        else
        {
            subject = null;
        }

        Map options;
        if (matches(SEMI))
        {
            eat(SEMI);
            options = map();
        }
        else
        {
            options = null;
        }

        return new Address(name, subject, options);
    }

    public Map<Object,Object> map()
    {
        eat(LBRACE);

        Map<Object,Object> result = new HashMap<Object,Object>();
        while (true)
        {
            if (matches(NUMBER, STRING, ID, LBRACE, LBRACK))
            {
                keyval(result);
                if (matches(COMMA))
                {
                    eat(COMMA);
                }
                else if (matches(RBRACE))
                {
                    break;
                }
                else
                {
                    throw new ParseError(next(), COMMA, RBRACE);
                }
            }
            else if (matches(RBRACE))
            {
                break;
            }
            else
            {
                throw new ParseError(next(), NUMBER, STRING, ID, LBRACE, LBRACK,
                                     RBRACE);
            }
        }

        eat(RBRACE);
        return result;
    }

    void keyval(Map<Object,Object> map)
    {
        Object key = value();
        eat(COLON);
        Object val = value();
        map.put(key, val);
    }

    Object value()
    {
        if (matches(NUMBER, STRING, ID, TRUE, FALSE))
        {
            return tok2obj(eat());
        }
        else if (matches(LBRACE))
        {
            return map();
        }
        else if (matches(LBRACK))
        {
            return list();
        }
        else
        {
            throw new ParseError(next(), NUMBER, STRING, ID, LBRACE, LBRACK);
        }
    }

    List<Object> list()
    {
        eat(LBRACK);

        List<Object> result = new ArrayList<Object>();

        while (true)
        {
            if (matches(RBRACK))
            {
                break;
            }
            else
            {
                result.add(value());
                if (matches(COMMA))
                {
                    eat(COMMA);
                }
                else if (matches(RBRACK))
                {
                    break;
                }
                else
                {
                    throw new ParseError(next(), COMMA, RBRACK);
                }
            }
        }

        eat(RBRACK);
        return result;
    }

}
