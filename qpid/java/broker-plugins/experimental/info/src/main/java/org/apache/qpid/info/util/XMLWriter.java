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

import java.util.Map;

/**
 * 
 *  Naive and rudimentary XML writer
 *  It has methods to write the header, a tag with attributes 
 *  and values. It escapes the XML special characters 
 */
public class XMLWriter
{

    private final StringBuffer _sb;

    private final String INDENT = "    ";

    public XMLWriter(StringBuffer sb)
    {
        _sb = sb;
    }

    public StringBuffer getXML()
    {
        return _sb;
    }

    public void writeXMLHeader()
    {
        _sb.append("<?xml version=\"1.0\"?>\n");
    }

    public void writeTag(String tagName, Map<String, String> attributes,
            String value)
    {
        writeOpenTag(tagName, attributes);
        writeValue(value);
        writeCloseTag(tagName);
    }

    public void writeOpenTag(String tagName, Map<String, String> attributes)
    {
        _sb.append("<").append(tagName);
        if (null == attributes)
        {
            _sb.append(">\n");
            return;
        }
        for (String key : attributes.keySet())
        {
            _sb.append(" ").append(key + "=\"" + attributes.get(key) + "\"");
        }
        _sb.append(">\n");

    }

    private void writeValue(String val)
    {
        _sb.append(INDENT).append(escapeXML(val) + "\n");
    }

    public void writeCloseTag(String tagName)
    {
        _sb.append("</" + tagName + ">\n");
    }

    private String escapeXML(String xmlStr)
    {
        if (null == xmlStr)
            return null;
        xmlStr = xmlStr.replaceAll("&", "&amp;");
        xmlStr = xmlStr.replace("<", "&lt;");
        xmlStr = xmlStr.replace(">", "&gt;");
        xmlStr = xmlStr.replace("\"", "&quot;");
        xmlStr = xmlStr.replace("'", "&apos;");
        return xmlStr;
    }

}
