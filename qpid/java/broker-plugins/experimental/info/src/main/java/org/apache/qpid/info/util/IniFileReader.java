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

package org.apache.qpid.info.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * This class is simple implementation of an ini file reader. It expects a
 * file with the following structure:
 *
 * ; global values, can be overwritten in sections
 * key1=value1
 * key2=value2
 *
 * [Section1]
 * key1=value1_new  ; overwriting the global key1
 * key3=value3
 * key4=value4
 *
 * [Section2]
 * key5=value5
 * key6=value6
 * key7=value7
 *
 * Note: Commentaries are preceded by ; or # and are supported throughout
 * A commentary line at the end of section is interpreted as
 * a section end marker
 *
 * A structure <String,Properties> (section name, associated properties)
 * is generated as a result of processing the ini file.
 */
public class IniFileReader
{
    private final Map<String, Properties> _sections;

    private final String COMMENT_SEMICOLON = ";";

    private final String COMMENT_HASH = "#";

    enum State
    {
        IN_SECTION, OFF_SECTION, GLOBAL
    }

    /*
     * IniFileReader constructor
     */

    public IniFileReader()
    {
        _sections = new HashMap<String, Properties>();
    }

    /**
     * Cleans up the after comments or the empty spaces/tabs surrounding the given string
     *
     * @param str The String to be cleaned
     *
     * @return String Cleanup Version
     */
    private String cleanUp(String str)
    {
        if (str.contains(COMMENT_SEMICOLON))
        {
            str = str.substring(0, str.indexOf(COMMENT_SEMICOLON));
        }
        if (str.contains(COMMENT_HASH))
        {
            str = str.substring(0, str.indexOf(COMMENT_HASH));
        }
        return str.trim();
    }

    /**
     * Loads and parses the ini file with the full path specified in the argument
     *
     * @param fileName Full path to the ini file
     *
     * @throws IllegalArgumentException If the file cannot be processed
     */
    public void load(String fileName) throws IllegalArgumentException
    {
        if (!new File(fileName).isFile())
        {
            throw new IllegalArgumentException("File: " + fileName + " does not exist or cannot be read.");
        }
        State state = State.GLOBAL;
        String line;
        Properties sectionProps = new Properties();
        String sectionName = "";
        try
        {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            while ((line = in.readLine()) != null)
            {
                String str = cleanUp(line);

                // Did we get a section header?
                if (str.startsWith("["))
                {
                    if (!str.endsWith("]"))
                    {
                        // Index of 1 to skip '['
                        throw new IllegalArgumentException(str.substring(1)
                                                           + " is not closed");
                    }

                    // We encountered a new section header
                    if (state != State.IN_SECTION)
                    {
                        _sections.put(sectionName, sectionProps);
                        sectionProps = new Properties();
                        sectionName = str.replace("[", "").replace("]", "")
                                .trim();
                        state = State.IN_SECTION;
                    }
                }

                // Any other line tested separately, ignore if out of a section
                // and add if in section
                if (str.length() == 0)
                {
                    // We encountered a commented or an empty line, both cases
                    // mean we are off the section
                    if (state == State.IN_SECTION)
                    {
                        _sections.put(sectionName, sectionProps);
                        state = State.OFF_SECTION;
                    }
                }
                else
                {
                    // proper line, add it to the props
                    if (state != State.OFF_SECTION)
                    {
                        if (str.contains("="))
                        {
                            int ix = str.indexOf("=");
                            sectionProps.put(str.substring(0, ix).trim(), str
                                    .substring(ix + 1).trim());
                        }
                    }
                }
            }
            in.close();
        }
        catch (IOException e)
        {
            _sections.clear();
            return;
        }
        if (state != State.OFF_SECTION)
        {
            _sections.put(sectionName, sectionProps);
        }
    }

    /**
     * Getter for the Sections Map
     *
     * @return Map<String,Properties> The parsed content of the ini file in this structure
     */
    public Map<String, Properties> getSections()
    {
        return _sections;
    }

}
