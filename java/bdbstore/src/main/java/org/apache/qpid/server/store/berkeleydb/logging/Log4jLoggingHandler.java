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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    private static interface MappedLevel
    {
        public boolean isEnabled(final Logger logger);

        void log(Logger logger, String message);
    }

    private static final MappedLevel ERROR = new MappedLevel()
    {
        @Override
        public boolean isEnabled(final Logger logger)
        {
            return logger.isErrorEnabled();
        }

        @Override
        public void log(final Logger logger, final String message)
        {
            logger.error(message);
        }
    };

    private static final MappedLevel WARN = new MappedLevel()
    {
        @Override
        public boolean isEnabled(final Logger logger)
        {
            return logger.isWarnEnabled();
        }

        @Override
        public void log(final Logger logger, final String message)
        {
            logger.warn(message);
        }
    };

    private static final MappedLevel INFO = new MappedLevel()
    {
        @Override
        public boolean isEnabled(final Logger logger)
        {
            return logger.isInfoEnabled();
        }

        @Override
        public void log(final Logger logger, final String message)
        {
            logger.info(message);
        }
    };

    private static final MappedLevel DEBUG = new MappedLevel()
    {
        @Override
        public boolean isEnabled(final Logger logger)
        {
            return logger.isDebugEnabled();
        }

        @Override
        public void log(final Logger logger, final String message)
        {
            logger.debug(message);
        }
    };

    private static final MappedLevel TRACE = new MappedLevel()
    {
        @Override
        public boolean isEnabled(final Logger logger)
        {
            return logger.isTraceEnabled();
        }

        @Override
        public void log(final Logger logger, final String message)
        {
            logger.trace(message);
        }
    };

    private static final Map<Level, MappedLevel> LEVEL_MAP;
    static
    {
        Map<Level, MappedLevel> map = new HashMap<>();
        map.put(Level.SEVERE, ERROR);
        map.put(Level.WARNING, WARN);
        //Note that INFO comes out at DEBUG level as the BDB logging at INFO seems to be more of a DEBUG nature
        map.put(Level.INFO, DEBUG);
        map.put(Level.CONFIG, DEBUG);
        map.put(Level.FINE, TRACE);
        map.put(Level.FINER, TRACE);
        map.put(Level.FINEST, TRACE);
        map.put(Level.ALL, TRACE);

        LEVEL_MAP = Collections.unmodifiableMap(map);
    }

    private static final Logger BDB_LOGGER = LoggerFactory.getLogger("BDB");

    private boolean isEnabledFor(Level level)
    {
        final MappedLevel mappedLevel = LEVEL_MAP.get(level);
        return mappedLevel == null ? false : mappedLevel.isEnabled(BDB_LOGGER);
    }

    @Override
    public void publish(final LogRecord record)
    {
        MappedLevel level = convertLevel(record.getLevel());
        if (level.isEnabled(BDB_LOGGER))
        {

            Formatter formatter = getFormatter();

            try
            {
                String message = formatter.format(record);
                try
                {
                    level.log(BDB_LOGGER, message);
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
        MappedLevel mappedLevel = convertLevel(record.getLevel());

        return mappedLevel.isEnabled(BDB_LOGGER);
    }

    private MappedLevel convertLevel(final Level level)
    {
        //Note that INFO comes out at DEBUG level as the BDB logging at INFO seems to be more of a DEBUG nature
        MappedLevel result = LEVEL_MAP.get(level);
        if(result == null)
        {
            if (level.intValue() >= Level.SEVERE.intValue())
            {
                result = ERROR;
            }
            else if (level.intValue() >= Level.WARNING.intValue())
            {
                result = WARN;
            }
            else if (level.intValue() >= Level.CONFIG.intValue())
            {
                result = DEBUG;
            }
            else
            {
                result = TRACE;
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
