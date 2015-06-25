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
package org.apache.qpid.restapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.util.Lexer;
import org.apache.qpid.messaging.util.Lexicon;
import org.apache.qpid.messaging.util.ParseError;
import org.apache.qpid.messaging.util.Token;

/**
 * AddressParser
 *
 * This JSONMapParser class used is mostly just a direct copy of org.apache.qpid.messaging.util.AddressParser
 * as it provides a handy mechanism to parse a JSON String into a Map which is the only JSON requirement that
 * we really need for QMF.
 *
 * Unfortunately there's a restriction/bug on the core AddressParser whereby it serialises integers into Java Integer
 * which means that long integer values aren't correctly stored. It's this restriction that gives Java Address
 * Strings a defacto 2GB queue size. I should really provide a patch for the *real* AddressParser but it's better
 * to add features covering "shorthand" forms for large values (e.g. k/K, m/M, g/G for kilo, mega, giga etc.)
 * to both the Java and C++ AddressParser to ensure maximum consistency.
 *
 * This AddressParser clone largely uses the classes from org.apache.qpid.messaging.util like the real AddressParser
 * but unfortunately the Parser class was package scope rather than public, so I've done some "copy and paste reuse"
 * to add the Parser methods into this version of AddressParser. I've also removed the bits that actually create
 * an Address as all we need to do is to parse into a java.util.Map.
 */

public class JSONMapParser
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
    // Make test for true and false case insensitive. N.B. org.apache.qpid.messaging.util.AddressParser test is
    // case sensitive - not sure if that's a bug/oversight in the AddressParser??
    private static Token.Type TRUE = lxi.define("TRUE", "(?i)True");
    private static Token.Type FALSE = lxi.define("FALSE", "(?i)False");
    private static Token.Type ID = lxi.define("ID", "[a-zA-Z_](?:[a-zA-Z0-9_-]*[a-zA-Z0-9_])?");
    private static Token.Type STRING = lxi.define("STRING", "\"(?:[^\\\"]|\\.)*\"|'(?:[^\\']|\\.)*'");
    private static Token.Type ESC = lxi.define("ESC", "\\\\[^ux]|\\\\x[0-9a-fA-F][0-9a-fA-F]|\\\\u[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]");
    private static Token.Type SYM = lxi.define("SYM", "[.#*%@$^!+-]");
    private static Token.Type WSPACE = lxi.define("WSPACE", "[\\s]+");
    private static Token.Type EOF = lxi.eof("EOF");

    private static Lexer LEXER = lxi.compile();

/********** Copied from org.apache.qpid.messaging.util.Parser as Parser was package scope **********/

    private List<Token> tokens;
    private int idx = 0;

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

/***************************************************************************************************/

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

    /**
     * This method is the the main place where this class differs from org.apache.qpid.messaging.util.AddressParser.
     * If the token type is a STRING it checks for a number (with optional floating point) ending in K, M or G
     * and if it is of this type it creates a Long out of the float value multiplied by 1000, 1000000 or 1000000000.
     * If the token type is a NUMBER it tries to parse into an Integer like AddressParser, but if that fails it
     * tries to parse onto a Long which allows much larger integer values to be used.
     */
    static Object tok2obj(Token tok)
    {
        Token.Type type = tok.getType();
        String value = tok.getValue();
        if (type == STRING)
        {
            value = unquote(value, tok);

            // Initial regex to check for a number (with optional floating point) ending in K, M or G
            if (value.matches("([0-9]*\\.[0-9]+|[0-9]+)\\s*[kKmMgG]"))
            {
                // If it's a numeric string perform the relevant multiplication and return as a Long.
                int length = value.length();
                if (length > 1)
                {
                    String end = value.substring(length - 1, length).toUpperCase();
                    String start = value.substring(0, length - 1).trim();

                    if (end.equals("K"))
                    {
                        return Long.valueOf((long)(Float.parseFloat(start) * 1000.0));
                    }
                    else if (end.equals("M"))
                    {
                        return Long.valueOf((long)(Float.parseFloat(start) * 1000000.0));
                    }
                    else if (end.equals("G"))
                    {
                        return Long.valueOf((long)(Float.parseFloat(start) * 1000000000.0));
                    }
                }

                return value;
            }
            else
            {
                return value;
            }
        }
        else if (type == NUMBER)
        {
            // This block extends the original AddressParser handling of NUMBER. It first attempts to parse the String
            // into an Integer in order to be backwards compatible with AddressParser however if this causes a
            // NumberFormatException it then attempts to parse into a Long.
            if (value.indexOf('.') >= 0)
            {
                return Double.valueOf(value);
            }
            else
            {
                try
                {
                    return Integer.decode(value);
                }
                catch (NumberFormatException nfe)
                {
                    return Long.decode(value);
                }
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

    public JSONMapParser(String input)
    {
        this.tokens = wlex(input); // Copied from org.apache.qpid.messaging.util.Parser
        this.idx = 0; // Copied from org.apache.qpid.messaging.util.Parser
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
