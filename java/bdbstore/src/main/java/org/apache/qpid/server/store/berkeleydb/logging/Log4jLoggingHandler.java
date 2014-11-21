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
package org.apache.qpid.server.store.berkeleydb.logging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;


public class Log4jLoggingHandler extends Handler
{
    public Log4jLoggingHandler(final String prefix)
    {
        setFormatter(new Formatter()
        {
            @Override
            public String format(final LogRecord record)
            {
                return prefix + " " + formatMessage(record);
            }
        });
    }

    private static final Map<Level, org.apache.log4j.Level> LEVEL_MAP;
    static
    {
        Map<Level, org.apache.log4j.Level> map = new HashMap<>();
        map.put(Level.SEVERE, org.apache.log4j.Level.ERROR);
        map.put(Level.WARNING, org.apache.log4j.Level.WARN);
        //Note that INFO comes out at DEBUG level as the BDB logging at INFO seems to be more of a DEBUG nature
        map.put(Level.INFO, org.apache.log4j.Level.DEBUG);
        map.put(Level.CONFIG, org.apache.log4j.Level.DEBUG);
        map.put(Level.FINE, org.apache.log4j.Level.TRACE);
        map.put(Level.FINER, org.apache.log4j.Level.TRACE);
        map.put(Level.FINEST, org.apache.log4j.Level.TRACE);
        map.put(Level.ALL, org.apache.log4j.Level.TRACE);

        LEVEL_MAP = Collections.unmodifiableMap(map);
    }

    private static final org.apache.log4j.Logger BDB_LOGGER = org.apache.log4j.Logger.getLogger("BDB");


    @Override
    public void publish(final LogRecord record)
    {
        org.apache.log4j.Level level = convertLevel(record.getLevel());
        if (BDB_LOGGER.isEnabledFor(level))
        {

            Formatter formatter = getFormatter();

            try
            {
                String message = formatter.format(record);
                try
                {
                    BDB_LOGGER.log(level, message);
                }
                catch (RuntimeException e)
                {
                    reportError(null, e, ErrorManager.WRITE_FAILURE);
                }
            }
            catch (RuntimeException e)
            {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
            }
        }
    }

    @Override
    public boolean isLoggable(final LogRecord record)
    {
        return BDB_LOGGER.isEnabledFor(convertLevel(record.getLevel()));
    }

    private org.apache.log4j.Level convertLevel(final Level level)
    {
        //Note that INFO comes out at DEBUG level as the BDB logging at INFO seems to be more of a DEBUG nature
        org.apache.log4j.Level result = LEVEL_MAP.get(level);
        if(result == null)
        {
            if (level.intValue() >= Level.SEVERE.intValue())
            {
                result = org.apache.log4j.Level.ERROR;
            }
            else if (level.intValue() >= Level.WARNING.intValue())
            {
                result = org.apache.log4j.Level.WARN;
            }
            else if (level.intValue() >= Level.CONFIG.intValue())
            {
                result = org.apache.log4j.Level.DEBUG;
            }
            else
            {
                result = org.apache.log4j.Level.TRACE;
            }
        }

        return result;
    }

    @Override
    public void flush()
    {

    }

    @Override
    public void close() throws SecurityException
    {

    }
}
