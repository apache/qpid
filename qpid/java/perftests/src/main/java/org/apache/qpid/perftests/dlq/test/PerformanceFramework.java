package org.apache.qpid.perftests.dlq.test;

import static org.apache.qpid.perftests.dlq.client.Config.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run a series of different performance tests, based on a set of variations of
 * configuration properties, and collect the results and generated statistics.
 */
public class PerformanceFramework
{
    private static final Logger _log = LoggerFactory.getLogger(PerformanceFramework.class);
    private static final String _date = new SimpleDateFormat("yyyyMMdd").format(new Date());
    
    private static File _dir;
    
    private Properties _props;
    private int _id = 0;

    public PerformanceFramework(File propertyFile)
    {
        try
        {
            InputStream input = new FileInputStream(propertyFile);
            _props = new Properties();
            _props.load(input);
        }
        catch (IOException ioe)
        {
            throw new RuntimeException("file error with " + propertyFile.getName());
        }
    }
    
    public PerformanceFramework(Properties props)
    {
        _props = props;
    }
    
    public void runAll(File file) throws Exception
    {
        _log.info("starting test framework");
        PrintStream out = new PrintStream(new FileOutputStream(file));
        out.println("id,maxRedelivery,rejectCount,messageIds,listener,session");
        for (int maxRedelivery = 0; maxRedelivery < 4; maxRedelivery++)
        {
            _props.setProperty(MAX_REDELIVERY, Integer.toString(maxRedelivery));
	        for (int rejectCount = 0; rejectCount < maxRedelivery + 1; rejectCount++)
	        {
	            _props.setProperty(REJECT_COUNT, Integer.toString(rejectCount));
	            for (int messageIds = 0; messageIds < 2; messageIds++)
	            {
		            _props.setProperty(MESSAGE_IDS, Boolean.toString(messageIds == 0));
		            for (int listener = 0; listener < 2; listener++)
		            {
			            _props.setProperty(LISTENER, Boolean.toString(listener == 0));
			            for (int session = 0; session < 4; session++)
			            {
				            _props.setProperty(SESSION, SESSION_VALUES[session]);
			                _id++;
				            out.println(_id + "," + maxRedelivery + "," + rejectCount + "," +
				            		Boolean.toString(messageIds == 0) + "," + Boolean.toString(listener == 0) +
				            		"," + SESSION_VALUES[session]);
					        if (!runOnce(_id))
					        {
					            return;
					        }
		                }
		            }
	            }
	        }
        }
    }
    
    public boolean runOnce(int id)
    {
        PerformanceStatistics stats = new PerformanceStatistics(_props);
        try
        {
            _log.info("starting test id " + id);
            String fileId = String.format("%04d", id);
	        if (stats.series(new File(_dir, fileId + "-series.csv")))
	        {
	            _log.info("test id " + id + " completed ok");
		        stats.statistics(new File(_dir, fileId + "-statistics.csv"));
	        }
	        else
	        {
	            _log.error("connection failure, test series aborted");
	            return false;
	        }
        }
        catch (Exception e)
        {
            _log.error("failed test id " + id + " with error", e);
        }
        return true;
    }

    public static void main(String[] argv) throws Exception
    {
        if (argv.length != 1)
        {
            throw new IllegalArgumentException("must pass name of property file as argument");
        }
        
        File propertyFile = new File(argv[0]);
        if (!propertyFile.exists() || !propertyFile.canRead())
        {
            throw new RuntimeException("property file '" + propertyFile.getAbsolutePath() + "' must exist and be readable");
        }
        
        int dirId = 0;
        while (true)
        {
	        _dir = new File(String.format("%s-%02d", _date, dirId++));
	        if (_dir.exists())
	        {
	            continue;
	        }
	        else
	        {
		        _dir.mkdir();
		        break;
	        }
        }
        
        PerformanceFramework framework = new PerformanceFramework(propertyFile);
        framework.runAll(new File(_dir, "framework.csv"));
    }
}
