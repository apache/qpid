package org.apache.qpid.perftests.dlq.test;

import java.util.Arrays;
import java.util.List;

public class Statistics
{
    private String _data;
    private double _mean;
    private double _standardDeviation;
    private double _tValue;
    private double _min = Double.MAX_VALUE;
    private double _max = Double.MIN_VALUE;
    
    // Actually calculated using =TINV() in Excel, but see Students t-Test
    // in any good statistics reference and you should find these values.
    // These are for a 95% confidence interval for n-2 (note) samples.
    private List<Double> tDistribution95 = Arrays.asList(
        12.70620473d, 4.30265273d, 3.182446305d, 2.776445105d, 2.570581835d,
        2.446911846d, 2.364624251d, 2.306004133d, 2.262157158d, 2.228138842d,
        2.200985159d, 2.178812827d, 2.160368652d, 2.144786681d, 2.131449536d,
        2.119905285d, 2.109815559d, 2.100922037d, 2.09302405d, 2.085963441d,
        2.079613837d, 2.073873058d, 2.068657599d, 2.063898547d, 2.059538536d
    );
    
    public Statistics(List<Double> samples, String data)
    {
        _data = data;
        
        double n = (double) samples.size();
        double total = 0.0d;
        for (Double s : samples)
        {
            total += s;
            _min = Math.min(_min, s);
            _max = Math.max(_max, s);
        }
        _mean = total / n;
        double deviationSquared = 0.0d;
        for (Double s : samples)
        {
            double deviation = s - _mean;
            deviationSquared += (deviation * deviation);
        }
        _standardDeviation = Math.sqrt(deviationSquared / (n - 1));
        _tValue = tDistribution95.get((int) (n - 2)) * (_standardDeviation / Math.sqrt(n));
    }
    
    public String getData()
    {
        return _data;
    }
    
    public double getMean()
    {
        return _mean;
    }
    
    public double getMin()
    {
        return _min;
    }
    
    public double getMax()
    {
        return _max;
    }
    
    public double getStandardDeviation()
    {
        return _standardDeviation;
    }
    
    public double getIntervalMin()
    {
        return _mean - _tValue;
    }
    
    public double getIntervalMax()
    {
        return _mean + _tValue;
    }
    
    public double getInterval()
    {
        return 2.0d * _tValue;
    }
    
    public double getRange()
    {
        return getMax() - getMin();
    }
    
    public static String getHeader()
    {
        return "data,mean,min,max,range,stdev,min95,max95,int95";
    }
    
    public String toString()
    {
        String results = String.format("%s,%f,%f,%f,%f,%f,%f,%f,%f",
                _data,
                getMean(),
                getMin(), getMax(), getRange(),
                getStandardDeviation(),
                getIntervalMin(), getIntervalMax(), getInterval());
        return results;
    }
}
