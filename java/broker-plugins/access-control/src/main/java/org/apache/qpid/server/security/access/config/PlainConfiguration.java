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
package org.apache.qpid.server.security.access.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;

public class PlainConfiguration extends AbstractConfiguration
{
    private static final Logger _logger = Logger.getLogger(PlainConfiguration.class);

    public static final Character COMMENT = '#';
    public static final Character CONTINUATION = '\\';

    public static final String ACL = "acl";
    public static final String CONFIG = "config";

    static final String UNRECOGNISED_INITIAL_MSG = "Unrecognised initial token '%s' at line %d";
    static final String NOT_ENOUGH_TOKENS_MSG = "Not enough tokens at line %d";
    static final String NUMBER_NOT_ALLOWED_MSG = "Number not allowed before '%s' at line %d";
    static final String CANNOT_LOAD_MSG = "Cannot load config file %s";
    static final String CANNOT_CLOSE_MSG = "Cannot close config file %s";
    static final String PREMATURE_CONTINUATION_MSG = "Premature continuation character at line %d";
    static final String PREMATURE_EOF_MSG = "Premature end of file reached at line %d";
    static final String PARSE_TOKEN_FAILED_MSG = "Failed to parse token at line %d";
    static final String CONFIG_NOT_FOUND_MSG = "Cannot find config file %s";
    static final String NOT_ENOUGH_ACL_MSG = "Not enough data for an acl at line %d";
    static final String NOT_ENOUGH_CONFIG_MSG = "Not enough data for config at line %d";
    static final String BAD_ACL_RULE_NUMBER_MSG = "Invalid rule number at line %d";
    static final String PROPERTY_KEY_ONLY_MSG = "Incomplete property (key only) at line %d";
    static final String PROPERTY_NO_EQUALS_MSG = "Incomplete property (no equals) at line %d";
    static final String PROPERTY_NO_VALUE_MSG = "Incomplete property (no value) at line %d";

    private StreamTokenizer _st;

    public PlainConfiguration(File file)
    {
        super(file);
    }

    @Override
    public RuleSet load()
    {
        RuleSet ruleSet = super.load();

        File file = getFile();
        FileReader fileReader = null;

        try
        {
            if(_logger.isDebugEnabled())
            {
                _logger.debug("About to load ACL file " + file);
            }

            fileReader = new FileReader(file);
            _st = new StreamTokenizer(new BufferedReader(fileReader));
            _st.resetSyntax(); // setup the tokenizer

            _st.commentChar(COMMENT); // single line comments
            _st.eolIsSignificant(true); // return EOL as a token
            _st.ordinaryChar('='); // equals is a token
            _st.ordinaryChar(CONTINUATION); // continuation character (when followed by EOL)
            _st.quoteChar('"'); // double quote
            _st.quoteChar('\''); // single quote
            _st.whitespaceChars('\u0000', '\u0020'); // whitespace (to be ignored) TODO properly
            _st.wordChars('a', 'z'); // unquoted token characters [a-z]
            _st.wordChars('A', 'Z'); // [A-Z]
            _st.wordChars('0', '9'); // [0-9]
            _st.wordChars('_', '_'); // underscore
            _st.wordChars('-', '-'); // dash
            _st.wordChars('.', '.'); // dot
            _st.wordChars('*', '*'); // star
            _st.wordChars('@', '@'); // at
            _st.wordChars(':', ':'); // colon

            // parse the acl file lines
            Stack<String> stack = new Stack<String>();
            int current;
            do {
                current = _st.nextToken();
                switch (current)
                {
                    case StreamTokenizer.TT_EOF:
                    case StreamTokenizer.TT_EOL:
                        if (stack.isEmpty())
                        {
                            break; // blank line
                        }

                        // pull out the first token from the bottom of the stack and check arguments exist
                        String first = stack.firstElement();
                        stack.removeElementAt(0);
                        if (stack.isEmpty())
                        {
                            throw new IllegalConfigurationException(String.format(NOT_ENOUGH_TOKENS_MSG, getLine()));
                        }

                        // check for and parse optional initial number for ACL lines
                        Integer number = null;
                        if (StringUtils.isNumeric(first))
                        {
                            // set the acl number and get the next element
                            number = Integer.valueOf(first);
                            first = stack.firstElement();
                            stack.removeElementAt(0);
                        }

                        if (StringUtils.equalsIgnoreCase(ACL, first))
                        {
                            parseAcl(number, stack);
                        }
                        else if (number == null)
                        {
                            if(StringUtils.equalsIgnoreCase("GROUP", first))
                            {
                                throw new IllegalConfigurationException(String.format("GROUP keyword not supported. Groups should defined via a Group Provider, not in the ACL file.", getLine()));
                            }
                            else if (StringUtils.equalsIgnoreCase(CONFIG, first))
                            {
                                parseConfig(stack);
                            }
                            else
                            {
                                throw new IllegalConfigurationException(String.format(UNRECOGNISED_INITIAL_MSG, first, getLine()));
                            }
                        }
                        else
                        {
                            throw new IllegalConfigurationException(String.format(NUMBER_NOT_ALLOWED_MSG, first, getLine()));
                        }

                        // reset stack, start next line
                        stack.clear();
                        break;
                    case StreamTokenizer.TT_NUMBER:
                        stack.push(Integer.toString(Double.valueOf(_st.nval).intValue()));
                        break;
                    case StreamTokenizer.TT_WORD:
                        stack.push(_st.sval); // token
                        break;
                    default:
                        if (_st.ttype == CONTINUATION)
                        {
                            int next = _st.nextToken();
                            if (next == StreamTokenizer.TT_EOL)
                            {
	                            break; // continue reading next line
                            }

                            // invalid location for continuation character (add one to line beacuse we ate the EOL)
                            throw new IllegalConfigurationException(String.format(PREMATURE_CONTINUATION_MSG, getLine() + 1));
                        }
                        else if (_st.ttype == '\'' || _st.ttype == '"')
                        {
                            stack.push(_st.sval); // quoted token
                        }
                        else
                        {
                            stack.push(Character.toString((char) _st.ttype)); // single character
                        }
                }
            } while (current != StreamTokenizer.TT_EOF);

            if (!stack.isEmpty())
            {
                throw new IllegalConfigurationException(String.format(PREMATURE_EOF_MSG, getLine()));
            }
        }
        catch (IllegalArgumentException iae)
        {
            throw new IllegalConfigurationException(String.format(PARSE_TOKEN_FAILED_MSG, getLine()), iae);
        }
        catch (FileNotFoundException fnfe)
        {
            throw new IllegalConfigurationException(String.format(CONFIG_NOT_FOUND_MSG, file.getName()), fnfe);
        }
        catch (IOException ioe)
        {
            throw new IllegalConfigurationException(String.format(CANNOT_LOAD_MSG, file.getName()), ioe);
        }
        finally
        {
            if(fileReader != null)
            {
                try
                {
                    fileReader.close();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException(String.format(CANNOT_CLOSE_MSG, file.getName()), e);
                }
            }
        }


        return ruleSet;
    }

    private void parseAcl(Integer number, List<String> args)
    {
        if (args.size() < 3)
        {
            throw new IllegalConfigurationException(String.format(NOT_ENOUGH_ACL_MSG, getLine()));
        }

        Permission permission = Permission.parse(args.get(0));
        String identity = args.get(1);
        Operation operation = Operation.parse(args.get(2));

        if (number != null && !getConfiguration().isValidNumber(number))
        {
            throw new IllegalConfigurationException(String.format(BAD_ACL_RULE_NUMBER_MSG, getLine()));
        }

        if (args.size() == 3)
        {
            getConfiguration().grant(number, identity, permission, operation);
        }
        else
        {
            ObjectType object = ObjectType.parse(args.get(3));
            AclRulePredicates predicates = toRulePredicates(args.subList(4, args.size()));

            getConfiguration().grant(number, identity, permission, operation, object, predicates);
        }
    }

    private void parseConfig(List<String> args)
    {
        if (args.size() < 3)
        {
            throw new IllegalConfigurationException(String.format(NOT_ENOUGH_CONFIG_MSG, getLine()));
        }

        Map<String, Boolean> properties = toPluginProperties(args);

        getConfiguration().configure(properties);
    }

    private AclRulePredicates toRulePredicates(List<String> args)
    {
        AclRulePredicates predicates = new AclRulePredicates();
        Iterator<String> i = args.iterator();
        while (i.hasNext())
        {
            String key = i.next();
            if (!i.hasNext())
            {
                throw new IllegalConfigurationException(String.format(PROPERTY_KEY_ONLY_MSG, getLine()));
            }
            if (!"=".equals(i.next()))
            {
                throw new IllegalConfigurationException(String.format(PROPERTY_NO_EQUALS_MSG, getLine()));
            }
            if (!i.hasNext())
            {
                throw new IllegalConfigurationException(String.format(PROPERTY_NO_VALUE_MSG, getLine()));
            }
            String value = i.next();

            predicates.parse(key, value);
        }
        return predicates;
    }

    /** Converts a {@link List} of "name", "=", "value" tokens into a {@link Map}. */
    protected Map<String, Boolean> toPluginProperties(List<String> args)
    {
        Map<String, Boolean> properties = new HashMap<String, Boolean>();
        Iterator<String> i = args.iterator();
        while (i.hasNext())
        {
            String key = i.next().toLowerCase();
            if (!i.hasNext())
            {
                throw new IllegalConfigurationException(String.format(PROPERTY_KEY_ONLY_MSG, getLine()));
            }
            if (!"=".equals(i.next()))
            {
                throw new IllegalConfigurationException(String.format(PROPERTY_NO_EQUALS_MSG, getLine()));
            }
            if (!i.hasNext())
            {
                throw new IllegalConfigurationException(String.format(PROPERTY_NO_VALUE_MSG, getLine()));
            }

            // parse property value and save
            Boolean value = Boolean.valueOf(i.next());
            properties.put(key, value);
        }
        return properties;
    }

    protected int getLine()
    {
        return _st.lineno() - 1;
    }
}
