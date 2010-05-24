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

/**
 * 
 * @author sorin
 * 
 *  Info object
 */

package org.apache.qpid.info;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.qpid.info.util.XMLWriter;

public class Info<T extends Map<String, ?>>
{
    private T _info;

    public Info(T info)
    {
        _info = info;
    }

    public String toString()
    {
        String result = "";
        for (Iterator<String> it = _info.keySet().iterator(); it.hasNext();)
        {
            String str = it.next();
            result += str + "=" + _info.get(str).toString() + "\n";
        }
        return result;
    }

    public Properties toProps()
    {
        Properties props = new Properties();
        if (null == _info)
            return null;
        for (Iterator<String> it = _info.keySet().iterator(); it.hasNext();)
        {
            String key = it.next();
            props.put(key, _info.get(key));
        }
        return props;
    }

    public StringBuffer toStringBuffer()
    {
        StringBuffer sb = new StringBuffer();
        for (Iterator<String> it = _info.keySet().iterator(); it.hasNext();)
        {
            String str = it.next();
            sb.append(str + "=" + _info.get(str).toString() + "\n");
        }
        return sb;
    }

    public StringBuffer toXML()
    {
        XMLWriter xw = new XMLWriter(new StringBuffer());
        xw.writeXMLHeader();
        Map<String, String> attr = new HashMap<String, String>();
        xw.writeOpenTag("qpidinfo", attr);
        String key;
        for (Iterator<String> it = _info.keySet().iterator(); it.hasNext();)
        {
            attr.clear();
            key = it.next();
            xw.writeTag(key, attr, _info.get(key).toString());
        }
        xw.writeCloseTag("qpidinfo");
        return xw.getXML();
    }

}
