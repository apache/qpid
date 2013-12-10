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
package org.apache.qpid.amqp_1_0.codec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultDescribedTypeConstructor extends DescribedTypeConstructor
{
    private Object _descriptor;

    public DefaultDescribedTypeConstructor(final Object descriptor)
    {
        _descriptor = descriptor;
    }

    public Object construct(final Object underlying)
    {
        return new DescribedType(_descriptor, underlying);
    }


    public static void main(String[] args) throws IOException, ParseException
    {
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
        String line;
        Pattern pattern = Pattern.compile("^\\d+ (\\d{4}-\\d{2}-\\d{2} \\d\\d:\\d\\d:\\d\\d,\\d\\d\\d)");

        long prevTime = Long.MAX_VALUE;

        while((line = reader.readLine()) != null)
        {
            Matcher m = pattern.matcher(line);
            if(m.matches())
            {
                String timeStr = m.group(1);
                long time = df.parse(timeStr).getTime();
                if(time - prevTime > 20000)
                {
                    System.out.println(df.format(prevTime) + " - " + df.format(time));
                }
                prevTime = time;
            }
        }
    }
}
