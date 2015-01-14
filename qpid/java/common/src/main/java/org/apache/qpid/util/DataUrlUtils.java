/*
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
 */

package org.apache.qpid.util;

import javax.xml.bind.DatatypeConverter;

public class DataUrlUtils
{
    public static String getDataUrlForBytes(final byte[] bytes)
    {
        StringBuilder inlineURL = new StringBuilder("data:;base64,");
        inlineURL.append(DatatypeConverter.printBase64Binary(bytes));
        return inlineURL.toString();
    }
}
