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
package org.apache.qpid.amqp_1_0.jms.impl.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class JsonDecoder
{
    static enum TokenType
    {
        BEGIN_MAP,
        END_MAP,
        BEGIN_ARRAY,
        END_ARRAY,
        COMMA,
        COLON,
        STRING,
        BOOLEAN,
        NUMBER,
        NULL
    }

    static private class Token
    {
        private final TokenType _type;
        private final Object _value;


        private Token(final TokenType type, final Object value)
        {
            _type = type;
            _value = value;
        }

        public TokenType getType()
        {
            return _type;
        }

        public Object getValue()
        {
            return _value;
        }
    }

    public Object decode(Reader reader) throws IOException
    {
        if(!reader.markSupported())
        {
            return decode(new BufferedReader(reader));
        }
        else
        {
            return readValue(reader, new Stack<Token>());
        }
    }

    private Object readValue(final Reader reader, final Stack<Token> tokenStack) throws IOException
    {
        Token token = readToken(reader, tokenStack);
        switch(token.getType())
        {
            case BOOLEAN:
            case NUMBER:
            case STRING:
            case NULL:
                return token.getValue();
            case BEGIN_MAP:
                Map<Object,Object> map = new LinkedHashMap<>();
                token = readToken(reader, tokenStack);
                if(token.getType() != TokenType.END_MAP)
                {
                    tokenStack.push(token);
                    do
                    {
                        Object key = readValue(reader, tokenStack);
                        token = readToken(reader, tokenStack);
                        if(token.getType() != TokenType.COLON)
                        {
                            throw new IllegalArgumentException("Cannot parse Json string");
                        }
                        Object value = readValue(reader, tokenStack);
                        map.put(key, value);
                        token = readToken(reader, tokenStack);
                        if(!(token.getType() == TokenType.END_MAP
                            || token.getType() == TokenType.COMMA))
                        {
                            throw new IllegalArgumentException("Cannot parse Json string");
                        }
                    }
                    while(token.getType() != TokenType.END_MAP);

                }
                return map;
            case BEGIN_ARRAY:
                List<Object> list = new ArrayList<>();
                token = readToken(reader, tokenStack);
                if(token.getType() != TokenType.END_MAP)
                {
                    tokenStack.push(token);
                    do
                    {
                        Object element = readValue(reader, tokenStack);
                        list.add(element);
                        token = readToken(reader, tokenStack);
                        if(!(token.getType() == TokenType.END_ARRAY
                           || token.getType() == TokenType.COMMA))
                        {
                            throw new IllegalArgumentException("Cannot parse Json string");
                        }
                    }
                    while(token.getType() != TokenType.END_ARRAY);

                }

                return list;
            default:
                throw new IllegalArgumentException("Could not parse Json String");

        }
    }

    static final Map<Character, Token> PUNCTUATION_TOKENS;
    static
    {
        final Map<Character, Token> tokenMap = new HashMap<>();
        tokenMap.put('{', new Token(TokenType.BEGIN_MAP, null));
        tokenMap.put('}', new Token(TokenType.END_MAP, null));
        tokenMap.put('[', new Token(TokenType.BEGIN_ARRAY, null));
        tokenMap.put(']', new Token(TokenType.END_ARRAY, null));
        tokenMap.put(':', new Token(TokenType.COLON, null));
        tokenMap.put(',', new Token(TokenType.COMMA, null));
        PUNCTUATION_TOKENS = Collections.unmodifiableMap(tokenMap);
    }

    private Token readToken(final Reader reader, final Stack<Token> tokenStack) throws IOException
    {
        if(!tokenStack.isEmpty())
        {
            return tokenStack.pop();
        }
        ignoreWhitespace(reader);


        char[] cb = new char[1];
        reader.mark(2);
        if(reader.read(cb) == 1)
        {
            final char c = cb[0];
            Token token = PUNCTUATION_TOKENS.get(c);
            if(token != null)
            {
                return token;
            }
            if(c == '"')
            {
                reader.reset();
                return readString(reader);
            }
            else if(c == '-' || (c >= '0' && c <= '9'))
            {
                reader.reset();
                return readNumber(reader);
            }
            else if(c == 't')
            {
                reader.reset();
                readLiteral(reader, "true");
                return new Token(TokenType.BOOLEAN, true);
            }
            else if(c == 'f')
            {
                reader.reset();
                readLiteral(reader, "false");
                return new Token(TokenType.BOOLEAN, false);
            }
            else if(c == 'n')
            {
                reader.reset();
                readLiteral(reader, "null");
                return new Token(TokenType.NULL, null);
            }
            else
            {
                throw new IllegalArgumentException("Could not parse json string");
            }
        }
        else
        {
            throw new IllegalArgumentException("Insufficient data");
        }

    }

    private Token readNumber(final Reader reader) throws IOException
    {
        StringBuilder buffer = new StringBuilder();
        reader.mark(1);
        char[] cb = new char[1];
        int read;
        while((read = reader.read(cb)) == 1 && (Character.isDigit(cb[0]) || cb[0] == '-' || Character.isAlphabetic(cb[0]) || cb[0] == '.'))
        {
            buffer.append(cb[0]);
            reader.mark(1);
        }
        if(read == 1)
        {
            reader.reset();
        }

        // todo - here
        String numberString = buffer.toString();
        if(!numberString.matches("-?\\d+(\\.\\d+)?([eE][+\\-]?\\d+)?"))
        {
            throw new IllegalArgumentException("Cannot parse number from " + numberString);
        }
        BigDecimal number = new BigDecimal(numberString.toUpperCase());
        try
        {
            // TODO - doesn't cope with unsigned longs > Long.MAX_VALUE
            BigInteger bigInteger = number.toBigIntegerExact();
            if(bigInteger.longValue() > Integer.MAX_VALUE
                    || bigInteger.longValue() < Integer.MIN_VALUE)
            {
                return new Token(TokenType.NUMBER, bigInteger.longValue());
            }
            else
            {
                return new Token(TokenType.NUMBER, bigInteger.intValue());
            }
        }
        catch(ArithmeticException e)
        {
            return new Token(TokenType.NUMBER, number.doubleValue());
        }
    }

    private Token readString(final Reader reader) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        // ignore starting quote
        reader.read();


        do
        {
            char c = readChar(reader);
            if(c == '\\')
            {
                c = readChar(reader);
                if(c == '\\' || c == '/' || c == '"')
                {
                    builder.append(c);
                }
                else if(c == 't')
                {
                    builder.append('\t');
                }
                else if(c == 'n')
                {
                    builder.append('\n');
                }
                else if(c == 'r')
                {
                    builder.append('\r');
                }
                else if(c == 'f')
                {
                    builder.append('\f');
                }
                else if(c == 'b')
                {
                    builder.append('\b');
                }
                else if(c == 'u')
                {
                    char[] point = new char[4];
                    if(reader.read(point) != 4)
                    {
                        throw new IllegalArgumentException("Insufficient data");
                    }
                    char codePoint = (char)(Integer.parseInt((new String(point)).toUpperCase(), 16) & 0xffff);
                    builder.append(codePoint);
                }
                else
                {
                    throw new IllegalArgumentException("Invalid escaped character");
                }
            }
            else if(c == '"')
            {
                break;
            }
            else
            {
                builder.append(c);
            }
        }
        while(true);


        return new Token(TokenType.STRING, builder.toString());
    }

    private char readChar(final Reader reader) throws IOException
    {
        char[] cb = new char[1];
        if(reader.read(cb) != 1)
        {
            throw new IllegalArgumentException("Insufficient data");
        }
        return cb[0];
    }


    private void readLiteral(final Reader reader, CharSequence expected) throws IOException
    {
        char[] cbuf = new char[expected.length()];
        int read = reader.read(cbuf);
        if(read != expected.length())
        {
            throw new IllegalArgumentException("Could not parse literal");
        }
        for(int i = 0; i < expected.length(); i++)
        {
            if(cbuf[i] != expected.charAt(i))
            {
                throw new IllegalArgumentException("Could not parse literal");
            }
        }
    }

    private void ignoreWhitespace(Reader reader) throws IOException
    {
        char[] cb = new char[1];
        reader.mark(1);
        while(reader.read(cb) == 1)
        {
            if(!Character.isWhitespace(cb[0]))
            {
                reader.reset();
                break;
            }
            else
            {
                reader.mark(1);
            }
        }
    }
}
