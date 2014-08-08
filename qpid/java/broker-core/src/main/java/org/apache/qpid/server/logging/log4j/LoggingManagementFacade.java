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
package org.apache.qpid.server.logging.log4j;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.apache.log4j.xml.Log4jEntityResolver;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.SystemUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A facade over log4j that allows both the control of the runtime logging behaviour (that is, the ability to
 * turn {@link Logger} on, off and control their {@link Level}, and the manipulation and reload
 * of the log4j configuration file.
 */
public class LoggingManagementFacade
{
    private static Logger LOGGER;
    private static transient LoggingManagementFacade _instance;
    private final String _filename;
    private final int _delay;

    public static LoggingManagementFacade configure(String filename) throws LoggingFacadeException
    {
        _instance = new LoggingManagementFacade(filename);
        return _instance;
    }

    public static LoggingManagementFacade configureAndWatch(String filename, int delay) throws LoggingFacadeException
    {
        _instance = new LoggingManagementFacade(filename, delay);
        return _instance;
    }

    public static LoggingManagementFacade getCurrentInstance()
    {
        return _instance;
    }

    private LoggingManagementFacade(String filename)
    {
        DOMConfigurator.configure(filename);

        if(LOGGER == null)
        {
            LOGGER = Logger.getLogger(LoggingManagementFacade.class);
        }
        _filename = filename;
        _delay = 0;
    }

    private LoggingManagementFacade(String filename, int delay)
    {
        DOMConfigurator.configureAndWatch(filename, delay);

        if(LOGGER == null)
        {
            LOGGER = Logger.getLogger(LoggingManagementFacade.class);
        }

        _filename = filename;
        _delay = delay;
    }

    public int getLog4jLogWatchInterval()
    {
        return _delay;
    }

    public synchronized void reload() throws LoggingFacadeException
    {
        DOMConfigurator.configure(_filename);
    }

    /** The log4j XML configuration file DTD defines three possible element
     * combinations for specifying optional logger+level settings.
     * Must account for the following:
     * <p>
     * {@literal <category name="x"> <priority value="y"/> </category>} OR
     * {@literal <category name="x"> <level value="y"/> </category>} OR
     * {@literal <logger name="x"> <level value="y"/> </logger>}
     * <p>
     * Noting also that the level/priority child element is optional too,
     * and not the only possible child element.
     */
    public synchronized Map<String,String> retrieveConfigFileLoggersLevels() throws LoggingFacadeException
    {
        try
        {
            Map<String,String> loggerLevelList = new HashMap<String,String>();
            LOGGER.info("Getting logger levels from log4j configuration file");

            Document doc = parseConfigFile(_filename);
            List<Element> categoryOrLoggerElements = buildListOfCategoryOrLoggerElements(doc);

            for (Element categoryOrLogger : categoryOrLoggerElements)
            {

                Element priorityOrLevelElement;
                try
                {
                    priorityOrLevelElement = getPriorityOrLevelElement(categoryOrLogger);
                }
                catch (LoggingFacadeException lfe)
                {
                    //there is no exiting priority or level to view, move onto next category/logger
                    continue;
                }

                String categoryName = categoryOrLogger.getAttribute("name");
                String priorityOrLevelValue = priorityOrLevelElement.getAttribute("value");
                loggerLevelList.put(categoryName, priorityOrLevelValue);
            }

            return loggerLevelList;
        }
        catch (IOException e)
        {
            throw new LoggingFacadeException(e);
        }
    }

    /**
     * The log4j XML configuration file DTD defines 2 possible element
     * combinations for specifying the optional root logger level settings
     * Must account for the following:
     * <p>
     * {@literal <root> <priority value="y"/> </root> } OR
     * {@literal <root> <level value="y"/> </root>}
     * <p>
     * Noting also that the level/priority child element is optional too,
     * and not the only possible child element.
     */
    public synchronized String retrieveConfigFileRootLoggerLevel() throws LoggingFacadeException
    {
        try
        {
            Document doc = parseConfigFile(_filename);

            //retrieve the optional 'root' element node
            NodeList rootElements = doc.getElementsByTagName("root");

            if (rootElements.getLength() == 0)
            {
                //there is no root logger definition
                return "N/A";
            }

            Element rootElement = (Element) rootElements.item(0);
            Element levelElement = getPriorityOrLevelElement(rootElement);

            if(levelElement != null)
            {
                return levelElement.getAttribute("value");
            }
            else
            {
                return "N/A";
            }
        }
        catch (IOException e)
        {
            throw new LoggingFacadeException(e);
        }
    }

    public synchronized void setConfigFileLoggerLevel(String logger, String level) throws LoggingFacadeException
    {
        LOGGER.info("Setting level to " + level + " for logger '" + logger
                + "' in log4j xml configuration file: " + _filename);

        try
        {
            Document doc = parseConfigFile(_filename);

            List<Element> logElements = buildListOfCategoryOrLoggerElements(doc);

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
                throw new LoggingFacadeException("Can't find logger " + logger);
            }

            Element levelElement = getPriorityOrLevelElement(logElement);

            //update the element with the new level/priority
            levelElement.setAttribute("value", level);

            //output the new file
            writeUpdatedConfigFile(_filename, doc);
        }
        catch (IOException ioe)
        {
            throw new LoggingFacadeException(ioe);
        }
        catch (TransformerConfigurationException e)
        {
            throw new LoggingFacadeException(e);
        }
    }

    public synchronized void setConfigFileRootLoggerLevel(String level) throws LoggingFacadeException
    {
        try
        {
            LOGGER.info("Setting level to " + level + " for the Root logger in " +
                    "log4j xml configuration file: " + _filename);

            Document doc = parseConfigFile(_filename);

            //retrieve the optional 'root' element node
            NodeList rootElements = doc.getElementsByTagName("root");

            if (rootElements.getLength() == 0)
            {
                throw new LoggingFacadeException("Configuration contains no root element");
            }

            Element rootElement = (Element) rootElements.item(0);
            Element levelElement = getPriorityOrLevelElement(rootElement);

            //update the element with the new level/priority
            levelElement.setAttribute("value", level);

            //output the new file
            writeUpdatedConfigFile(_filename, doc);
        }
        catch (IOException e)
        {
            throw new LoggingFacadeException(e);
        }
        catch (TransformerConfigurationException e)
        {
            throw new LoggingFacadeException(e);
        }
    }

    public List<String> getAvailableLoggerLevels()
    {
        return new ArrayList<String>()
        {
            private static final long serialVersionUID = 599203507907836466L;
        {
           add(Level.ALL.toString());
           add(Level.TRACE.toString());
           add(Level.DEBUG.toString());
           add(Level.INFO.toString());
           add(Level.WARN.toString());
           add(Level.ERROR.toString());
           add(Level.FATAL.toString());
           add(Level.OFF.toString());
        }};
    }

    public String retrieveRuntimeRootLoggerLevel()
    {
        Logger rootLogger = Logger.getRootLogger();
        return rootLogger.getLevel().toString();
    }

    public void setRuntimeRootLoggerLevel(String level)
    {
        Level newLevel = Level.toLevel(level);

        LOGGER.info("Setting RootLogger level to " + level);

        Logger log = Logger.getRootLogger();
        log.setLevel(newLevel);
    }

    public void setRuntimeLoggerLevel(String loggerName, String level) throws LoggingFacadeException
    {
        Level newLevel = level == null ? null : Level.toLevel(level);

        Logger targetLogger = findRuntimeLogger(loggerName);

        if(targetLogger == null)
        {
            throw new LoggingFacadeException("Can't find logger " + loggerName);
        }

        LOGGER.info("Setting level to " + newLevel + " for logger '" + targetLogger.getName() + "'");

        targetLogger.setLevel(newLevel);
    }

    public Map<String,String> retrieveRuntimeLoggersLevels()
    {
        LOGGER.info("Getting levels for currently active log4j loggers");

        Map<String, String> levels = new HashMap<String, String>();
        @SuppressWarnings("unchecked")
        Enumeration<Logger> loggers = LogManager.getCurrentLoggers();

        while (loggers.hasMoreElements())
        {
            Logger logger = loggers.nextElement();
            levels.put(logger.getName(), logger.getEffectiveLevel().toString());
        }

        return levels;
    }

    private void writeUpdatedConfigFile(String log4jConfigFileName, Document doc) throws IOException, TransformerConfigurationException
    {
        File log4jConfigFile = new File(log4jConfigFileName);

        if (!log4jConfigFile.canWrite())
        {
            LOGGER.warn("Specified log4j XML configuration file is not writable: " + log4jConfigFile);
            throw new IOException("Specified log4j XML configuration file is not writable");
        }

        // Swap temp file in to replace existing configuration file.
        File old = new File(log4jConfigFile.getAbsoluteFile() + ".old");
        if (old.exists())
        {
            old.delete();
        }

        if(!SystemUtils.isWindows())
        {

            File tmp;
            Random r = new Random();

            final String absolutePath = log4jConfigFile.getAbsolutePath();
            do
            {
                tmp = new File(absolutePath + r.nextInt() + ".tmp");
            }
            while(tmp.exists());

            tmp.deleteOnExit();

            writeConfigToFile(doc, new FileOutputStream(tmp));

            if(!log4jConfigFile.renameTo(old))
            {
                //unable to rename the existing file to the backup name
                LOGGER.error("Could not backup the existing log4j XML file");
                throw new IOException("Could not backup the existing log4j XML file");
            }

            if(!tmp.renameTo(new File(absolutePath)))
            {
                //failed to rename the new file to the required filename

                if(!old.renameTo(log4jConfigFile))
                {
                    //unable to return the backup to required filename
                    LOGGER.error("Could not rename the new log4j configuration file into place, and unable to restore original file");
                    throw new IOException("Could not rename the new log4j configuration file into place, and unable to restore original file");
                }

                LOGGER.error("Could not rename the new log4j configuration file into place");
                throw new IOException("Could not rename the new log4j configuration file into place");
            }
        }
        else
        {
            // In windows we can't do a safe rename current -> old, tmp -> current as it will not allow
            // a new file with the same name as current to be created while it is still open.

            // Instead we have to do an unsafe "copy current to old", "replace current contents with tmp contents"
            FileUtils.copy(log4jConfigFile,old);
            writeConfigToFile(doc, new FileOutputStream(log4jConfigFile));
        }
    }

    private void writeConfigToFile(Document doc, FileOutputStream outputFile) throws TransformerConfigurationException, IOException
    {
        Transformer transformer = null;
        transformer = TransformerFactory.newInstance().newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "log4j.dtd");
        DOMSource source = new DOMSource(doc);


        try
        {
            StreamResult result = new StreamResult(outputFile);
            transformer.transform(source, result);
        }
        catch (TransformerException e)
        {
            LOGGER.warn("Could not transform the XML into new file: ", e);
            throw new IOException("Could not transform the XML into new file: ", e);
        }
    }

    //method to parse the XML configuration file, validating it in the process, and returning a DOM Document of the content.
    private static Document parseConfigFile(String fileName) throws IOException
    {
        //check file was specified, exists, and is readable
        if(fileName == null)
        {
            LOGGER.warn("Provided log4j XML configuration filename is null");
            throw new IOException("Provided log4j XML configuration filename is null");
        }

        File configFile = new File(fileName);

        if (!configFile.exists())
        {
            LOGGER.warn("The log4j XML configuration file could not be found: " + fileName);
            throw new IOException("The log4j XML configuration file could not be found");
        }
        else if (!configFile.canRead())
        {
            LOGGER.warn("The log4j XML configuration file is not readable: " + fileName);
            throw new IOException("The log4j XML configuration file is not readable");
        }

        //parse it
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;
        Document doc;

        ErrorHandler errHandler = new QpidLog4JSaxErrorHandler();
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
            LOGGER.warn("Unable to parse the log4j XML file due to possible configuration error: ", e);
            throw new IOException("Unable to parse the log4j XML file due to possible configuration error: ", e);
        }
        catch (SAXException e)
        {
            LOGGER.warn("The specified log4j XML file is invalid: ", e);
            throw new IOException("The specified log4j XML file is invalid: ", e);
        }
        catch (IOException e)
        {
            LOGGER.warn("Unable to parse the specified log4j XML file", e);
            throw new IOException("Unable to parse the specified log4j XML file: ", e);
        }

        return doc;
    }

    private Logger findRuntimeLogger(String loggerName)
    {
        Logger targetLogger = null;
        @SuppressWarnings("unchecked")
        Enumeration<Logger> loggers = LogManager.getCurrentLoggers();
        while(loggers.hasMoreElements())
        {
            targetLogger = loggers.nextElement();
            if (targetLogger.getName().equals(loggerName))
            {
                return targetLogger;
            }
        }
        return null;
    }

    private List<Element> buildListOfCategoryOrLoggerElements(Document doc)
    {
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
        return logElements;
    }

    private Element getPriorityOrLevelElement(Element categoryOrLogger) throws LoggingFacadeException
    {
        //retrieve the optional 'priority' or 'level' sub-element value.
        //It may not be the only child node, so request by tag name.
        NodeList priorityElements = categoryOrLogger.getElementsByTagName("priority");
        NodeList levelElements = categoryOrLogger.getElementsByTagName("level");

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
            throw new LoggingFacadeException("Configuration " + categoryOrLogger.getNodeName()
                    + " element contains neither priority nor level child");
        }
        return levelElement;
    }

    private static class QpidLog4JSaxErrorHandler implements ErrorHandler
    {
        public void error(SAXParseException e) throws SAXException
        {
            if(LOGGER != null)
            {
                LOGGER.warn(constructMessage("Error parsing XML file", e));
            }
            else
            {
                System.err.println(constructMessage("Error parsing XML file", e));
            }
        }

        public void fatalError(SAXParseException e) throws SAXException
        {
            throw new SAXException(constructMessage("Fatal error parsing XML file", e));
        }

        public void warning(SAXParseException e) throws SAXException
        {
            if(LOGGER != null)
            {
                LOGGER.warn(constructMessage("Warning parsing XML file", e));
            }
            else
            {
                System.err.println(constructMessage("Warning parsing XML file", e));
            }
        }

        private static String constructMessage(final String msg, final SAXParseException ex)
        {
            return msg + ": Line " + ex.getLineNumber()+" column " +ex.getColumnNumber() + ": " + ex.getMessage();
        }
    }
}

