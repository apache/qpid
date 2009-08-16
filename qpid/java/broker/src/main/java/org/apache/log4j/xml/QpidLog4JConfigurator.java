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
package org.apache.log4j.xml;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.management.LoggingManagementMBean;
import org.apache.qpid.server.logging.management.LoggingManagementMBean.QpidLog4JSaxErrorHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * Substitute for the Log4J XMLWatchdog (as used by DOMConfigurator.configureAndWatch)
 * 
 * Extends the default behaviour with a strict parser check on the XML file before allowing the reconfiguration to proceed,
 * ensuring that any parser error or warning prevents initiation of a configuration update by Log4J, which aborts mid-update
 * upon fatal errors from the parser and proceeds in the event of 'regular' parser errors and warnings, in all cases allowing
 * startup to proceed with whatever half-baked configuration then exists.
 */
public class QpidLog4JConfigurator
{
    //lock to protect access to the configuration file
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static Logger _logger;

    private QpidLog4JConfigurator()
    {
        //no instances
    }
    
    public static void configure(String filename) throws IOException, ParserConfigurationException, 
                                                         SAXException, IllegalLoggerLevelException
    {
        try
        {
            LOCK.lock();

            strictlyParseXMLConfigFile(filename);
            checkLoggerLevels(filename);

            DOMConfigurator.configure(filename);
        }
        finally
        {
            LOCK.unlock();
        }
    }
    
    public static void configureAndWatch(String filename, long delay) throws IOException, ParserConfigurationException, 
                                                                             SAXException, IllegalLoggerLevelException
    {
        strictlyParseXMLConfigFile(filename);
        checkLoggerLevels(filename);
        
        QpidLog4JXMLWatchdog watchdog = new QpidLog4JXMLWatchdog(filename);
        watchdog.setDelay(delay);
        watchdog.start();
    }
    
    private static void strictlyParseXMLConfigFile(String fileName) throws IOException, SAXException,
                                                                                        ParserConfigurationException
    {
        try
        {
            LOCK.lock();

            //check file was specified, exists, and is readable
            if(fileName == null)
            {
                throw new IOException("Provided log4j XML configuration filename was null");
            }

            File configFile = new File(fileName);

            if (!configFile.exists())
            {
                throw new IOException("The log4j XML configuration file does not exist: " + fileName);
            }
            else if (!configFile.canRead())
            {
                throw new IOException("The log4j XML configuration file is not readable: " + fileName);
            }

            //parse it
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;

            ErrorHandler errHandler = new QpidLog4JSaxErrorHandler();

            docFactory.setValidating(true);
            docBuilder = docFactory.newDocumentBuilder();
            docBuilder.setErrorHandler(errHandler);
            docBuilder.setEntityResolver(new Log4jEntityResolver());
            docBuilder.parse(fileName);
        }
        finally
        {
            LOCK.unlock();
        }
    }
    
    private static class QpidLog4JXMLWatchdog extends XMLWatchdog
    {
        public QpidLog4JXMLWatchdog(String filename)
        {
            super(filename);
        }

        public void doOnChange()
        {
            try
            {
                LOCK.lock();
                
                try
                {
                    strictlyParseXMLConfigFile(filename);
                }
                catch (Exception e)
                {
                    //logger will be instantiated following first configuration success, which has been pre-validated
                    //and so the null check should never actually be required.
                    if(_logger != null)
                    {
                        _logger.warn("Parsing the log4j XML configuration file generated errors/warnings. " +
                                "The new configuration was not applied. Correct the issues to prompt " +
                                "another update attempt: " + e.getMessage());
                    }
                    return;
                }

                try
                {
                    checkLoggerLevels(filename);
                }
                catch (Exception e)
                {
                    //logger will be instantiated following first configuration success, which has been pre-validated
                    //and so the null check should never actually be required.
                    if(_logger != null)
                    {
                        _logger.warn("Errors were found when validating the logger level values in the " +
                                "log4j XML configuration file. The new configuration was not applied. " +
                                "Correct the issues to prompt another update attempt: " + e.getMessage());
                    }
                    return;
                }

                //everything checked was ok, let the normal update process proceed
                super.doOnChange();

                //a configuration has now been applied, enable logging for future attempts
                if(_logger == null)
                {
                    _logger = Logger.getLogger(QpidLog4JConfigurator.class);
                }

                _logger.info("Applied log4j configuration from: " + filename);
            }
            finally
            {
                LOCK.unlock();
            }

        }
    }
    
    protected static void checkLoggerLevels(String filename) throws IllegalLoggerLevelException, IOException
    {
        //check that the logger levels specified in the XML are actually valid

        try
        {
            LOCK.lock();
            
            Map<String, String> loggersLevels;
            loggersLevels = LoggingManagementMBean.retrieveConfigFileLoggersLevels(filename);

            for (String loggerName : loggersLevels.keySet())
            {
                String levelString = loggersLevels.get(loggerName);
                checkLevel(loggerName,levelString);
            }

            //check the root logger level
            String rootLoggerlevelString = LoggingManagementMBean.retrieveConfigFileRootLoggerLevel(filename);
            checkLevel("Root", rootLoggerlevelString);
        }
        finally
        {
            LOCK.unlock();
        }
    }
    
    private static void checkLevel(String loggerName, String levelString) throws IllegalLoggerLevelException
    {
        if("null".equalsIgnoreCase(levelString) || "inherited".equalsIgnoreCase(levelString))
        {
            //the string "null" signals to inherit from a parent logger 
            return;
        }
        
        Level level = Level.toLevel(levelString);

        //above Level.toLevel call returns a DEBUG Level if the request fails. Check the result.
        if (level.equals(Level.DEBUG) && !(levelString.equalsIgnoreCase("debug")))
        {
            //received DEBUG but we did not ask for it, the Level request failed.
            throw new IllegalLoggerLevelException("Level '" + levelString + "' specified for Logger '" + loggerName + "' is invalid");
        }
    }
    
    public static class IllegalLoggerLevelException extends Exception
    {
        private static final long serialVersionUID = 1L;

        public IllegalLoggerLevelException(String msg)
        {
            super(msg);
        }
    }
}

