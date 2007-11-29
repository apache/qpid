package org.apache.qpid.client.perf;

public class Options
{
    int _messageSize;
    boolean _transacted;
    boolean _synchronous;
    String _destination;
    long _expiry;
    long _logFrequency;
    String _logFilePath;

    /**
     * System props
     * -DmessageSize
     * -DuseQueue
     * -Dtransacted
     * -Ddestinations
     * -DlogFilePath
     * -Duration=1H,30M,10S
     * -DlogDuration=10  in mins
     */
    public void parseOptions()
    {
        _messageSize = Integer.parseInt(System.getProperty("messageSize","100"));
        _transacted = false;
        _destination = System.getProperty("destinations");
        _logFrequency = Long.parseLong(System.getProperty("logFrequency","1000"));
        _logFilePath = System.getProperty("logFilePath");
        _expiry = getExpiry();

        System.out.println("============= Test Data ===================");
        System.out.println("Destination            : " + _destination);
        System.out.println("Collect stats          : " + Boolean.getBoolean("collect_stats"));
        System.out.println("Log Frequency in msgs  : " + _logFrequency);
        System.out.println("Log file path          : " + _logFilePath);
        System.out.println("Test Duration          : " + printTestDuration());
        System.out.println("============= /Test Data ===================");
    }

    private String printTestDuration()
    {
        StringBuffer buf = new StringBuffer();
        long temp = _expiry;
        int hours = (int)temp/(60*60*1000);
        temp = temp -hours*60*60*1000;

        int mins  = (int)(temp)/(60*1000);
        temp = temp -mins*60*1000;

        int secs  = (int)temp/1000;

        if (hours > 0)
        {
            buf.append(hours).append(" hours ");
        }
        if (mins > 0)
        {
            buf.append(mins).append(" mins ");
        }
        if (secs > 0)
        {
            buf.append(secs).append(" secs");
        }

        return buf.toString();
    }

    private long getExpiry()
    {
        // default is 30 mins
        long time = 0;
        String s = System.getProperty("duration");
        if(s != null)
        {
            String[] temp = s.split(",");
            for (String st:temp)
            {
                if(st.indexOf("H")>0)
                {
                    int hour = Integer.parseInt(st.substring(0,st.indexOf("H")));
                    time = time + hour * 60 * 60 * 1000;
                }
                else if(st.indexOf("M")>0)
                {
                    int min = Integer.parseInt(st.substring(0,st.indexOf("M")));
                    time = time + min * 60 * 1000;
                }
                else if(st.indexOf("S")>0)
                {
                    int sec = Integer.parseInt(st.substring(0,st.indexOf("S")));
                    time = time + sec * 1000;
                }

            }
        }
        if (time == 0)
        {
            time = 30 * 60 * 1000;
        }

        return time;
    }

}
