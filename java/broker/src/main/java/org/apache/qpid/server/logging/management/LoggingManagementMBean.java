/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.logging.management;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.AMQManagedObject;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.Log4jEntityResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.management.JMException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


/** MBean class for BrokerLoggingManagerMBean. It implements all the management features exposed for managing logging. */
@MBeanDescription("Logging Management Interface")
public class LoggingManagementMBean extends AMQManagedObject implements LoggingManagement
{

    private static final Logger _logger = Logger.getLogger(LoggingManagementMBean.class);
    private String _log4jConfigFileName;
    private int _log4jLogWatchInterval;
    
    static TabularType _loggerLevelTabularType;
    static CompositeType _loggerLevelCompositeType;

    static
    {
        try
        {
            OpenType[] loggerLevelItemTypes = new OpenType[]{SimpleType.STRING, SimpleType.STRING};

            _loggerLevelCompositeType = new CompositeType("LoggerLevelList", "Logger Level Data", 
                                                         COMPOSITE_ITEM_NAMES, COMPOSITE_ITEM_DESCRIPTIONS, loggerLevelItemTypes);

            _loggerLevelTabularType = new TabularType("LoggerLevel", "List of loggers with levels",
                                                       _loggerLevelCompositeType, TABULAR_UNIQUE_INDEX);
        }
        catch (OpenDataException e)
        {
            _logger.error("Tabular data setup for viewing logger levels was incorrect.");
            _loggerLevelTabularType = null;
        }
    }
    
    public LoggingManagementMBean(String log4jConfigFileName, int log4jLogWatchInterval) throws JMException
    {
        super(LoggingManagement.class, LoggingManagement.TYPE, LoggingManagement.VERSION);
        _log4jConfigFileName = log4jConfigFileName;
        _log4jLogWatchInterval = log4jLogWatchInterval;
    }

    public String getObjectInstanceName()
    {
        return LoggingManagement.TYPE;
    }
    
    public Integer getLog4jLogWatchInterval()
    {
        return _log4jLogWatchInterval;
    }

    @SuppressWarnings("unchecked")
    public synchronized boolean setRuntimeLoggerLevel(String logger, String level)
    {   
        //check specified level is valid
        Level newLevel;
        try
        {
            newLevel = getLevel(level);
        }
        catch (Exception e)
        {
            return false;
        }
        
        //check specified logger exists
        Enumeration loggers = LogManager.getCurrentLoggers();
        Boolean loggerExists = false;
        
        while(loggers.hasMoreElements())
        {
            Logger log = (Logger) loggers.nextElement();
            if (log.getName().equals(logger))
            {
                loggerExists = true;
                break;
            }
        }
        
        if(!loggerExists)
        {
            return false;
        }
        
        //set the logger to the new level
        _logger.info("Setting level to " + level + " for logger: " + logger);
        
        Logger log = Logger.getLogger(logger);
        log.setLevel(newLevel);
        
        return true;
    }
    
    @SuppressWarnings("unchecked")
    public synchronized TabularData viewEffectiveRuntimeLoggerLevels()
    {
        if (_loggerLevelTabularType == null)
        {
            _logger.warn("TabluarData type not set up correctly");
            return null;
        }

        _logger.info("Getting levels for currently active log4j loggers");
        
        Enumeration loggers = LogManager.getCurrentLoggers();

        TabularData loggerLevelList = new TabularDataSupport(_loggerLevelTabularType);

        Logger logger;
        String loggerName;
        String level;
        
        try
        {
            while(loggers.hasMoreElements()){
                logger = (Logger) loggers.nextElement();

                loggerName = logger.getName();
                level = logger.getEffectiveLevel().toString();

                Object[] itemData = {loggerName, level};
                CompositeData loggerData = new CompositeDataSupport(_loggerLevelCompositeType, COMPOSITE_ITEM_NAMES, itemData);
                loggerLevelList.put(loggerData);
            }
        }
        catch (OpenDataException e)
        {
            _logger.warn("Unable to create logger level list due to :" + e);
            return null;
        }

        return loggerLevelList;
        
    }
    
    public synchronized String getRuntimeRootLoggerLevel()
    {
        Logger rootLogger = Logger.getRootLogger();

        return rootLogger.getLevel().toString();
    }

    public synchronized boolean setRuntimeRootLoggerLevel(String level)
    {
            Level newLevel;
            try
            {
                newLevel = getLevel(level);
            }
            catch (Exception e)
            {
                return false;
            }
            
            _logger.info("Setting RootLogger level to " + level);
            
            Logger log = Logger.getRootLogger();
            log.setLevel(newLevel);
            
            return true;
    }
    
    //method to convert from a string to a log4j Level, throws exception if the given value is invalid
    private Level getLevel(String level) throws Exception
    {
        Level newLevel = Level.toLevel(level);
        
        //above Level.toLevel call returns a DEBUG Level if the request fails. Check the result.
        if (newLevel.equals(Level.DEBUG) && !(level.equalsIgnoreCase("debug")))
        {
            //received DEBUG but we did not ask for it, the Level request failed.
            throw new Exception("Invalid level name");
        }
        
        return newLevel;
    }
    
    //handler to catch errors signalled by the JAXP parser and throw an appropriate exception
    private class SaxErrorHandler implements ErrorHandler
    {
        
        public void error(SAXParseException e) throws SAXException
        {
            throw new SAXException("Error parsing XML file: " + e.getMessage());
        }

        public void fatalError(SAXParseException e) throws SAXException
        {
            throw new SAXException("Fatal error parsing XML file: " + e.getMessage());
        }

        public void warning(SAXParseException e) throws SAXException
        {
            throw new SAXException("Warning parsing XML file: " + e.getMessage());
        }
    }

    //method to parse the XML configuration file, validating it in the process, and returning a DOM Document of the content.
    private synchronized Document parseConfigFile(String fileName) throws IOException
    {
        //check file was specified, exists, and is readable
        if(fileName == null)
        {
            _logger.warn("No log4j XML configuration file has been set");
            throw new IOException("No log4j XML configuration file has been set");
        }

        File configFile = new File(fileName);

        if (!configFile.exists())
        {
            _logger.warn("Specified log4j XML configuration file does not exist: " + fileName);
            throw new IOException("Specified log4j XML configuration file does not exist");
        }
        else if (!configFile.canRead())
        {
            _logger.warn("Specified log4j XML configuration file is not readable: " + fileName);
            throw new IOException("Specified log4j XML configuration file is not readable");
        }

        //parse it
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;
        Document doc;

        ErrorHandler errHandler = new SaxErrorHandler();
        try
        {
            docFactory.setValidating(true);
            docBuilder = docFactory.newDocumentBuilder();
            docBuilder.setErrorHandler(errHandler);
            docBuilder.setEntityResolver(new Log4jEntityResolver());
            doc = docBuilder.parse(fileName);
        }
        catch (ParserConfigurationException e)
        {
            _logger.warn("Unable to parse the log4j XML file due to possible configuration error: " + e);
            //recommended that MBeans should use java.* and javax.* exceptions only
            throw new IOException("Unable to parse the log4j XML file due to possible configuration error: " + e.getMessage());
        }
        catch (SAXException e)
        {
            _logger.warn("The specified log4j XML file is invalid: " + e);
            //recommended that MBeans should use standard java.* and javax.* exceptions only
            throw new IOException("The specified log4j XML file is invalid: " + e.getMessage());
        }
        catch (IOException e)
        {
            _logger.warn("Unable to parse the specified log4j XML file" + e);
            throw new IOException("Unable to parse the specified log4j XML file", e);
        }

        return doc;
    }

    
    private synchronized boolean writeUpdatedConfigFile(String log4jConfigFileName, Document doc) throws IOException
    {
        File log4jConfigFile = new File(log4jConfigFileName);
        
        if (!log4jConfigFile.canWrite())
        {
            _logger.warn("Specified log4j XML configuration file is not writable: " + log4jConfigFile);
            throw new IOException("Specified log4j XML configuration file is not writable");
        }

        Transformer transformer = null;
        try
        {
            transformer = TransformerFactory.newInstance().newTransformer();
        }
        catch (Exception e)
        {
            _logger.warn("Could not create an XML transformer: " +e);
            return false;
        }

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "log4j.dtd");
        DOMSource source = new DOMSource(doc);
        
        File tmp;
        try
        {
            tmp = File.createTempFile("LogManMBeanTemp", ".tmp");
            tmp.deleteOnExit();
            StreamResult result = new StreamResult(tmp);
            transformer.transform(source, result);
        }
        catch (TransformerException e)
        {
            _logger.warn("Could not transform the XML into new file: " +e);
            return false;
        }
        catch (IOException e)
        {
            _logger.warn("Could not create the new file: " +e);
            return false;
        }

        // Swap temp file in to replace existing configuration file.
        File old = new File(log4jConfigFile.getAbsoluteFile() + ".old");
        if (old.exists())
        {
            old.delete();
        }
        log4jConfigFile.renameTo(old);
        return tmp.renameTo(log4jConfigFile);
    }


    /* The log4j XML configuration file DTD defines three possible element
     * combinations for specifying optional logger+level settings.
     * Must account for the following:
     * 
     * <category name="x"> <priority value="y"/> </category>    OR
     * <category name="x"> <level value="y"/> </category>    OR
     * <logger name="x"> <level value="y"/> </logger>
     *
     * Noting also that the level/priority child element is optional too,
     * and not the only possible child element.
     */
    
    
    public synchronized TabularData viewConfigFileLoggerLevels() throws IOException
    {
        if (_loggerLevelTabularType == null)
        {
            _logger.warn("TabluarData type not set up correctly");
            return null;
        }
        
        _logger.info("Getting logger levels from log4j configuration file");
        
        Document doc = parseConfigFile(_log4jConfigFileName);

        TabularData loggerLevelList = new TabularDataSupport(_loggerLevelTabularType);

        //retrieve the 'category' element nodes
        NodeList categoryElements = doc.getElementsByTagName("category");
        
        String categoryName;
        String priority = null;
        
        for (int i = 0; i < categoryElements.getLength(); i++)
        {
            Element categoryElement = (Element) categoryElements.item(i);
            categoryName = categoryElement.getAttribute("name");

            //retrieve the category's mandatory 'priority' or 'level' element's value.
            //It may not be the only child node, so request by tag name.
            NodeList priorityElements = categoryElement.getElementsByTagName("priority");
            NodeList levelElements = categoryElement.getElementsByTagName("level");

            if (priorityElements.getLength() != 0)
            {
                Element priorityElement = (Element) priorityElements.item(0);
                priority = priorityElement.getAttribute("value").toUpperCase();
            }
            else if (levelElements.getLength() != 0)
            {
                Element levelElement = (Element) levelElements.item(0);
                priority = levelElement.getAttribute("value").toUpperCase();
            }
            else
            {
                //there is no exiting priority or level to view, move onto next category/logger
                continue;
            }

            try
            {
                Object[] itemData = {categoryName, priority};
                CompositeData loggerData = new CompositeDataSupport(_loggerLevelCompositeType, COMPOSITE_ITEM_NAMES, itemData);
                loggerLevelList.put(loggerData);
            }
            catch (OpenDataException e)
            {
                _logger.warn("Unable to create logger level list due to :" + e);
                return null;
            }
        }

        //retrieve the 'logger' element nodes
        NodeList loggerElements = doc.getElementsByTagName("logger");
        
        String loggerName;
        String level;

        for (int i = 0; i < loggerElements.getLength(); i++)
        {
            Element loggerElement = (Element) loggerElements.item(i);
            loggerName = loggerElement.getAttribute("name");

            //retrieve the logger's mandatory 'level' element's value
            //It may not be the only child node, so request by tag name.
            NodeList levelElements = loggerElement.getElementsByTagName("level");

            Element levelElement = (Element) levelElements.item(0);
            level = levelElement.getAttribute("value").toUpperCase();
            
            try
            {
                Object[] itemData = {loggerName, level};
                CompositeData loggerData = new CompositeDataSupport(_loggerLevelCompositeType, COMPOSITE_ITEM_NAMES, itemData);
                loggerLevelList.put(loggerData);
            }
            catch (OpenDataException e)
            {
                _logger.warn("Unable to create logger level list due to :" + e);
                return null;
            }
        }
        
        return loggerLevelList;
    }

    public synchronized boolean setConfigFileLoggerLevel(String logger, String level) throws IOException
    {
        //check that the specified level is a valid log4j Level
        try
        {
            getLevel(level);
        }
        catch (Exception e)
        {
            //it isnt a valid level
            return false;
        }
        
        _logger.info("Setting level to " + level + " for logger '" + logger
                + "' in log4j xml configuration file: " + _log4jConfigFileName);
        
        Document doc = parseConfigFile(_log4jConfigFileName);

        //retrieve the 'category' and 'logger' element nodes
        NodeList categoryElements = doc.getElementsByTagName("category");
        NodeList loggerElements = doc.getElementsByTagName("logger");
        
        //collect them into a single elements list
        List<Element> logElements = new ArrayList<Element>();
        
        for (int i = 0; i < categoryElements.getLength(); i++)
        {
            logElements.add((Element) categoryElements.item(i));
        }
        for (int i = 0; i < loggerElements.getLength(); i++)
        {
            logElements.add((Element) loggerElements.item(i));
        }

        //try to locate the specified logger/category in the elements retrieved
        Element logElement = null;
        for (Element e : logElements)
        {
            if (e.getAttribute("name").equals(logger))
            {
                logElement = e;
                break;
            }
        }

        if (logElement == null)
        {
            //no loggers/categories with given name found, does not exist to update
            _logger.warn("Specified logger does not exist in the configuration file: " +logger);
            return false;
        }

        //retrieve the optional 'priority' or 'level' sub-element value.
        //It may not be the only child node, so request by tag name.
        NodeList priorityElements = logElement.getElementsByTagName("priority");
        NodeList levelElements = logElement.getElementsByTagName("level");

        Element levelElement = null;
        if (priorityElements.getLength() != 0)
        {
            levelElement = (Element) priorityElements.item(0);
        }
        else if (levelElements.getLength() != 0)
        {
            levelElement = (Element) levelElements.item(0);
        }
        else
        {
            //there is no exiting priority or level element to update
            return false;
        }
        
        //update the element with the new level/priority
        levelElement.setAttribute("value", level);
        
        //output the new file
        return writeUpdatedConfigFile(_log4jConfigFileName, doc);
    }

    
    /* The log4j XML configuration file DTD defines 2 possible element
     * combinations for specifying the optional root logger level settings
     * Must account for the following:
     * 
     * <root> <priority value="y"/> </root>    OR
     * <root> <level value="y"/> </root> 
     *
     * Noting also that the level/priority child element is optional too,
     * and not the only possible child element.
     */
    
    public synchronized String getConfigFileRootLoggerLevel() throws IOException
    {
        _logger.info("Getting root logger level from log4j configuration file");

        Document doc = parseConfigFile(_log4jConfigFileName);
       
        //retrieve the optional 'root' element node
        NodeList rootElements = doc.getElementsByTagName("root");

        if (rootElements.getLength() == 0)
        {
            //there is not root logger definition
            return null;
        }

        Element rootElement = (Element) rootElements.item(0);

        //retrieve the optional 'priority' or 'level' element value.
        //It may not be the only child node, so request by tag name.
        NodeList priorityElements = rootElement.getElementsByTagName("priority");
        NodeList levelElements = rootElement.getElementsByTagName("level");
        String priority = null;
        
        if (priorityElements.getLength() != 0)
        {
            Element priorityElement = (Element) priorityElements.item(0);
            priority = priorityElement.getAttribute("value");
        }
        else if(levelElements.getLength() != 0)
        {
            Element levelElement = (Element) levelElements.item(0);
            priority = levelElement.getAttribute("value");
        }

        if(priority != null)
        {
            return priority.toUpperCase();
        }
        else
        {
            return null;
        }
    }
    
    public synchronized boolean setConfigFileRootLoggerLevel(String level) throws IOException
    {
        //check that the specified level is a valid log4j Level
        try
        {
            getLevel(level);
        }
        catch (Exception e)
        {
            //it isnt a valid level
            return false;
        }
        
        _logger.info("Setting level to " + level + " for the Root logger in " +
        		"log4j xml configuration file: " + _log4jConfigFileName);

        Document doc = parseConfigFile(_log4jConfigFileName);
        
        //retrieve the optional 'root' element node
        NodeList rootElements = doc.getElementsByTagName("root");

        if (rootElements.getLength() == 0)
        {
            return false;
        }

        Element rootElement = (Element) rootElements.item(0);

        //retrieve the optional 'priority' or 'level' sub-element value.
        //It may not be the only child node, so request by tag name.
        NodeList priorityElements = rootElement.getElementsByTagName("priority");
        NodeList levelElements = rootElement.getElementsByTagName("level");

        Element levelElement = null;
        if (priorityElements.getLength() != 0)
        {
            levelElement = (Element) priorityElements.item(0);
        }
        else if (levelElements.getLength() != 0)
        {
            levelElement = (Element) levelElements.item(0);
        }
        else
        {
            //there is no exiting priority/level to update
            return false;
        }
        
        //update the element with the new level/priority
        levelElement.setAttribute("value", level);

        //output the new file
        return writeUpdatedConfigFile(_log4jConfigFileName, doc);
    }
}
