/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.qpid.server.logging;

import java.util.Iterator;
import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

public class LogRecorder implements Appender, Iterable<LogRecorder.Record>
{
    private ErrorHandler _errorHandler;
    private Filter _filter;
    private String _name;
    private long _recordId;

    private final int _bufferSize = 4096;
    private final int _mask = _bufferSize - 1;
    private Record[] _records = new Record[_bufferSize];


    public static class Record
    {
        private final long _id;
        private final String _logger;
        private final long _timestamp;
        private final String _threadName;
        private final String _level;
        private final String _message;


        public Record(long id, LoggingEvent event)
        {
            _id = id;
            _logger = event.getLoggerName();
            _timestamp = event.timeStamp;
            _threadName = event.getThreadName();
            _level = event.getLevel().toString();
            _message = event.getRenderedMessage();
        }

        public long getId()
        {
            return _id;
        }

        public long getTimestamp()
        {
            return _timestamp;
        }

        public String getThreadName()
        {
            return _threadName;
        }

        public String getLevel()
        {
            return _level;
        }

        public String getMessage()
        {
            return _message;
        }

        public String getLogger()
        {
            return _logger;
        }
    }

    public LogRecorder()
    {

        Logger.getRootLogger().addAppender(this);
    }

    @Override
    public void addFilter(Filter filter)
    {
        _filter = filter;
    }

    @Override
    public void clearFilters()
    {
        _filter = null;
    }

    @Override
    public void close()
    {
        //TODO - Implement
    }

    @Override
    public synchronized void doAppend(LoggingEvent loggingEvent)
    {
        _records[((int) (_recordId & _mask))] = new Record(_recordId, loggingEvent);
        _recordId++;
    }

    @Override
    public ErrorHandler getErrorHandler()
    {
        return _errorHandler;
    }

    @Override
    public Filter getFilter()
    {
        return _filter;
    }

    @Override
    public Layout getLayout()
    {
        return null;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public boolean requiresLayout()
    {
        return false;
    }

    @Override
    public void setErrorHandler(ErrorHandler errorHandler)
    {
        _errorHandler = errorHandler;
    }

    @Override
    public void setLayout(Layout layout)
    {

    }

    @Override
    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public Iterator<Record> iterator()
    {
        return new RecordIterator(Math.max(_recordId-_bufferSize, 0l));
    }

    private class RecordIterator implements Iterator<Record>
    {
        private long _id;

        public RecordIterator(long currentRecordId)
        {
            _id = currentRecordId;
        }

        @Override
        public boolean hasNext()
        {
            return _id < _recordId;
        }

        @Override
        public Record next()
        {
            Record record = _records[((int) (_id & _mask))];
            while(_id < _recordId-_bufferSize)
            {
                _id = _recordId-_bufferSize;
                record = _records[((int) (_id & _mask))];
            }
            _id++;
            return record;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
