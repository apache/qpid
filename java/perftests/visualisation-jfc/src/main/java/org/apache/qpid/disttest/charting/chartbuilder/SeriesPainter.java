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

import java.awt.BasicStroke;
import java.util.List;

import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.jfree.chart.JFreeChart;

public class SeriesPainter
{
    public void applySeriesAppearance(JFreeChart chart, List<SeriesDefinition> seriesDefinitions, SeriesStrokeAndPaintApplier strokeAndPaintApplier)
    {
        for (int i = 0; i < seriesDefinitions.size(); i++)
        {
            SeriesDefinition seriesDefinition = seriesDefinitions.get(i);
            if (seriesDefinition.getSeriesColourName() != null)
            {
                strokeAndPaintApplier.setSeriesPaint(i, ColorFactory.toColour(seriesDefinition.getSeriesColourName()), chart);
            }
            if (seriesDefinition.getStrokeWidth() != null)
            {
                // Negative width used to signify dashed
                boolean dashed = seriesDefinition.getStrokeWidth() < 0;
                float width = Math.abs(seriesDefinition.getStrokeWidth());
                BasicStroke stroke = buildStrokeOfWidth(width, dashed);
                strokeAndPaintApplier.setSeriesStroke(i, stroke, chart);
            }
        }
    }

    private BasicStroke buildStrokeOfWidth(float width, boolean dashed)
    {
        final BasicStroke stroke;
        if (dashed)
        {
            stroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, new float[] {5.0f, 3.0f}, 0.0f);
        }
        else
        {
            stroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        return stroke;
    }
}
