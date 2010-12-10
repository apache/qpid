package org.apache.qpid.perftests.dlq.test;

import static org.apache.qpid.perftests.dlq.client.Config.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceStatistics
{
    private static final Logger _log = LoggerFactory.getLogger(PerformanceStatistics.class);
    
    private Properties _props;
    private List<Double> _sent = new ArrayList<Double>();
    private List<Double> _received = new ArrayList<Double>();
    private List<Double> _rejected = new ArrayList<Double>();
    private List<Double> _duration = new ArrayList<Double>();
    private List<Double> _throughputIn = new ArrayList<Double>();
    private List<Double> _throughputOut = new ArrayList<Double>();
    private List<Double> _bandwidthIn = new ArrayList<Double>();
    private List<Double> _bandwidthOut = new ArrayList<Double>();
    private List<Double> _latency = new ArrayList<Double>();
    private List<Statistics> _statistics = new ArrayList<Statistics>();

    public PerformanceStatistics(File propertyFile)
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
    
    public PerformanceStatistics(Properties props)
    {
        _props = props;
    }
    
    public void single(PrintStream out) throws Exception
    {
        PerformanceTest client = new PerformanceTest(_props);
        client.test();
        client.check(out);
        _sent.add(client.getSent());
        _received.add(client.getReceived());
        _rejected.add(client.getRejected());
        _duration.add(client.getDuration());
        _throughputIn.add(client.getThroughputIn());
        _throughputOut.add(client.getThroughputOut());
        _bandwidthIn.add(client.getBandwidthIn());
        _bandwidthOut.add(client.getBandwidthOut());
        _latency.add(client.getLatency());
    }
    
    public void series(File file) throws Exception
    {
        try
        {
            PrintStream out = new PrintStream(new FileOutputStream(file));
            out.println(PerformanceTest.getHeader());
            int repeat = Integer.parseInt(_props.getProperty(REPEAT));
            for (int i = 0; i < repeat; i++)
            {
                _log.info("starting individual test run " + i);
                single(out);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("error running test series", e);
        }
        
        _statistics.add(new Statistics(_sent, "sent"));
        _statistics.add(new Statistics(_received, "received"));
        _statistics.add(new Statistics(_rejected, "rejected"));
        _statistics.add(new Statistics(_duration, "duration"));
        _statistics.add(new Statistics(_throughputIn, "throughputIn"));
        _statistics.add(new Statistics(_throughputOut, "throughputOut"));
        _statistics.add(new Statistics(_bandwidthIn, "bandwidthIn"));
        _statistics.add(new Statistics(_bandwidthOut, "bandwidthOut"));
        _statistics.add(new Statistics(_latency, "latency"));
    }
    
    public void statistics(File file)
    {
        try
        {
            PrintStream out = new PrintStream(new FileOutputStream(file));
	        out.println(Statistics.getHeader());
	        for (Statistics stats : _statistics)
	        {
	            out.println(stats.toString());
	        }
        }
        catch (Exception e)
        {
            throw new RuntimeException("error outputting stats", e);
        }
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
        
        PerformanceStatistics stats = new PerformanceStatistics(propertyFile);
        stats.series(new File("series.csv"));
        stats.statistics(new File("statistics.csv"));
    }
}
