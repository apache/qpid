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
package org.apache.qpid.disttest.charting.seriesbuilder;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;

import org.apache.qpid.disttest.charting.ChartingException;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;

public class JdbcCsvSeriesBuilder implements SeriesBuilder
{

    static
    {
        registerCsvDriver();
    }

    private SeriesBuilderCallback _callback;

    @Override
    public void setSeriesBuilderCallback(SeriesBuilderCallback callback)
    {
        this._callback = callback;
    }

    @Override
    public void build(List<SeriesDefinition> seriesDefinitions)
    {
        for (Iterator<SeriesDefinition> iterator = seriesDefinitions.iterator(); iterator.hasNext();)
        {
            SeriesDefinition series = iterator.next();
            buildDataSetForSingleSeries(series);
        }
    }

    private void buildDataSetForSingleSeries(SeriesDefinition seriesDefinition)
    {
        Connection conn = null;
        Statement stmt = null;
        try
        {
            File seriesDir = getValidatedSeriesDirectory(seriesDefinition);

            conn = DriverManager.getConnection("jdbc:relique:csv:" + seriesDir.getAbsolutePath());

            final String seriesStatement = seriesDefinition.getSeriesStatement();

            stmt = conn.createStatement();
            ResultSet results = stmt.executeQuery(seriesStatement);
            int columnCount = results.getMetaData().getColumnCount();
            _callback.beginSeries(seriesDefinition);
            while (results.next())
            {
                Object[] row = new Object[columnCount];
                for (int i = 0; i < row.length; i++)
                {
                    row[i] = results.getObject(i+1);
                }

                _callback.addDataPointToSeries(seriesDefinition, row);
            }
            _callback.endSeries(seriesDefinition);
        }
        catch (SQLException e)
        {
            throw new ChartingException("Failed to create chart dataset", e);
        }
        finally
        {
            if (stmt != null)
            {
                try
                {
                    stmt.close();
                }
                catch (SQLException e)
                {
                    throw new RuntimeException("Failed to close statement", e);
                }
            }
            if (conn != null)
            {
                try
                {
                    conn.close();
                }
                catch (SQLException e)
                {
                    throw new RuntimeException("Failed to close connection", e);
                }
            }
        }
    }

    private File getValidatedSeriesDirectory(SeriesDefinition series)
    {
        File seriesDir = new File(series.getSeriesDirectory());
        if (!seriesDir.isDirectory())
        {
            throw new ChartingException("seriesDirectory must be a directory : " + seriesDir);
        }
        return seriesDir;
    }

    private static void registerCsvDriver() throws ExceptionInInitializerError
    {
        try
        {
            Class.forName("org.relique.jdbc.csv.CsvDriver");
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException("Could not load CSV/JDBC driver", e);
        }
    }

}
