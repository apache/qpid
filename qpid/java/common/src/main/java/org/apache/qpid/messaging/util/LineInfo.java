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


/**
 * LineInfo
 *
 */

public class LineInfo
{

    public static LineInfo get(String st, int position)
    {
        int idx = 0;
        int line = 1;
        int column = 0;
        int line_pos = 0;
        while (idx < position)
        {
            if (st.charAt(idx) == '\n')
            {
                line += 1;
                column = 0;
                line_pos = idx;
            }

            column += 1;
            idx += 1;
        }

        int end = st.indexOf('\n', line_pos);
        if (end < 0)
        {
            end = st.length();
        }

        String text = st.substring(line_pos, end);

        return new LineInfo(line, column, text);
    }

    private int line;
    private int column;
    private String text;

    public LineInfo(int line, int column, String text)
    {
        this.line = line;
        this.column = column;
        this.text = text;
    }

    public int getLine()
    {
        return line;
    }

    public int getColumn()
    {
        return column;
    }

    public String getText()
    {
        return text;
    }

    public String toString()
    {
        return String.format("%s,%s:%s", line, column, text);
    }

}
