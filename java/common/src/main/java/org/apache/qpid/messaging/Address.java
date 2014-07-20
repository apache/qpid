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
package org.apache.qpid.messaging;

import org.apache.qpid.messaging.util.AddressParser;

import static org.apache.qpid.messaging.util.PyPrint.pprint;

import java.io.Serializable;
import java.util.Map;


/**
 * Address
 *
 */

public class Address implements Serializable
{

    private static final long serialVersionUID = 6096143531336726036L;

    private String _name;
    private String _subject;
    private Map _options;
    private final String _myToString;

    public static Address parse(String address)
    {
        return new AddressParser(address).parse();
    }

    public Address(String name, String subject, Map options)
    {
        this._name = name;
        this._subject = subject;
        this._options = options;
        this._myToString = String.format("%s/%s; %s", pprint(_name), pprint(_subject), pprint(_options));
    }

    public String getName()
    {
        return _name;
    }

    public String getSubject()
    {
        return _subject;
    }

    public Map getOptions()
    {
        return _options;
    }

    public String toString()
    {
        return _myToString;
    }

}
