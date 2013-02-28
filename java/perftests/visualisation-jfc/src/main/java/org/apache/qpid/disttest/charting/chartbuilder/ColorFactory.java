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
 *
 */
package org.apache.qpid.disttest.charting.chartbuilder;

import java.awt.Color;

public class ColorFactory
{
    /**
     * Converts a colour name known to the JDK into a {@link Color} instance.  Additionally,
     * if the work dark_ is prepended to the colour, a darker shade of the same colour is
     * produced.
     *
     * @param colourName
     * @return colour instance
     */
    public static Color toColour(String colourName)
    {
        boolean darkVersion = false;
        if (colourName.toLowerCase().startsWith("dark_"))
        {
            colourName = colourName.replaceFirst("(?i)dark_", "");
            darkVersion = true;
        }

        Color colour = getColourFromStaticField(colourName);
        if (darkVersion)
        {
            return colour.darker();
        }
        else
        {
            return colour;
        }
    }

    private static Color getColourFromStaticField(String colourName)
    {
        try
        {
            return (Color) Color.class.getField(colourName.toLowerCase()).get(null);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not find colour for " + colourName, e);
        }
    }

}
