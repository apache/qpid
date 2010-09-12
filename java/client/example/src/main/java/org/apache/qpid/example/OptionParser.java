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
package org.apache.qpid.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;

import org.apache.qpid.client.AMQConnection;

public class OptionParser
{    
    static final Option BROKER = new Option("b",
            "broker",
            "connect to specified broker",
            "USER:PASS@HOST:PORT",
            "guest:guest@localhost:5672",
            String.class);        
        
    static final Option HELP = new Option("h",
            "help",
            "show this help message and exit",
            null,
            null,
            Boolean.class);
    
    static final Option TIMEOUT = new Option("t",
            "timeout",
            "timeout in seconds to wait before exiting",
            "TIMEOUT",
            "0",
            Integer.class);
    
    static final Option CON_OPTIONS = new Option(null,
            "con-option",
            "JMS Connection URL options. Ex sync_ack=true sync_publish=all ",
            "NAME=VALUE",
            null,
            String.class);
    
    
    static final Option BROKER_OPTIONS = new Option(null,
            "broker-option",
            "JMS Broker URL options. Ex ssl=true sasl_mechs=GSSAPI ",
            "NAME=VALUE",
            null,
            String.class);
    
    
    protected Map<String,Object> optMap = new HashMap<String,Object>();
    protected static final List<Option> optDefs = new ArrayList<Option>();
    
    protected String usage;
    protected String desc;
    protected String address;
    
    public OptionParser(String[] args, String usage, String desc)
    {   
        this.usage = usage;
        this.desc  = desc;
        
        if (args.length == 0 || 
           (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help"))))
        {
            printHelp();
        }
        
        address = args[args.length -1];
        String[] ops = new String[args.length -1];
        System.arraycopy(args, 0, ops, 0, ops.length);        
        parseOpts(ops);
        
        System.out.println(optMap);
        
        if (isHelp())
        {
            printHelp();
        }
    }
    
    public boolean isHelp()
    {
        return optMap.containsKey("h") || optMap.containsKey("help");
    }
    
    public void printHelp()
    {
        System.out.println(String.format("%s\n",usage));
        System.out.println(String.format("%s\n",desc));
        System.out.println(String.format("%s\n","Options:"));
        
        for (Option op : optDefs)
        {  
           String valueLabel = op.getValueLabel() != null ? "=" + op.getValueLabel() : ""; 
           String shortForm = op.getShortForm() != null ? "-" + op.getShortForm() + valueLabel : "";
           String longForm = op.getLongForm() != null ? "--" + op.getLongForm() + valueLabel : "";
           String desc = op.getDesc();
           String defaultValue = op.getDefaultValue() != null ? 
                   " (default " + op.getDefaultValue() + ")" : "";
           
           if (!shortForm.equals(""))
           {
               longForm = shortForm + ", " + longForm;
           }
           System.out.println(
                   String.format("%-54s%s%s", longForm,desc,defaultValue));
        }
        
        System.exit(0);
    }
    
    private void parseOpts(String[] args)
    {   
        String prevOpt = null;
        for(String op: args)
        {
            // covers both -h and --help formats
            if (op.startsWith("-"))
            {
                String key = op.substring(op.startsWith("--")? 2:1 ,
                                         (op.indexOf('=') > 0) ? 
                                            op.indexOf('='):
                                            op.length());
                
                boolean match = false;
                for (Option option: optDefs)
                {
                    
                    if ((op.startsWith("-") && option.shortForm != null && option.shortForm.equals(key)) ||
                        (op.startsWith("--") && option.longForm != null && option.longForm.equals(key)) )
                    {
                        match = true;
                        break;
                    }
                }
                
                if (!match) 
                { 
                    System.out.println(op + " is not a valid option"); 
                    System.exit(0);
                }                    
                
                if (op.indexOf('=') > 0)
                {
                    String val = extractValue(op.substring(op.indexOf('=')+1));
                    if (optMap.containsKey(key))
                    {
                        optMap.put(key, optMap.get(key) + "," + val);
                    }
                    else
                    {
                        optMap.put(key, val);
                    }
                }
                else
                {
                    if (! optMap.containsKey(key)){ optMap.put(key, ""); }
                    prevOpt = key;
                }
            }
            else if (prevOpt != null) // this is to catch broker localhost:5672 instead broker=localhost:5672
            {
                String val = extractValue(op);
                if (optMap.containsKey(prevOpt) && !optMap.get(prevOpt).toString().equals(""))
                {
                    optMap.put(prevOpt, optMap.get(prevOpt) + "," + val);
                }
                else
                {
                    optMap.put(prevOpt, val);
                }
                prevOpt = null;
            }
            else
            {
                System.out.println(optMap);
                throw new IllegalArgumentException(op + " is not a valid option");
            }
        }
    }
    
    private String extractValue(String op)
    {
        if (op.startsWith("'"))
        {
            if (!op.endsWith("'")) 
                throw new IllegalArgumentException(" The option " + op + " needs to be inside quotes");
            
            return op.substring(1,op.length() -1);
        }
        else
        {
            return op;
        }
    }
    
    protected boolean containsOp(Option op)
    {
        return optMap.containsKey(op.shortForm) || optMap.containsKey(op.longForm);
    }
    
    protected String getOp(Option op)
    {
        if (optMap.containsKey(op.shortForm))
        {
            return (String)optMap.get(op.shortForm);
        }
        else if (optMap.containsKey(op.longForm))
        {
            return (String)optMap.get(op.longForm);
        }
        else
        {
            return op.getDefaultValue();
        }           
    }    

    protected Connection createConnection() throws Exception
    {
        StringBuffer buf;
        buf = new StringBuffer();       
        buf.append("amqp://");
        String userPass = "guest:guest";
        String broker = "localhost:5672";
        if(containsOp(BROKER))
        {
            try
            {
                String b = getOp(BROKER);
                userPass = b.substring(0,b.indexOf('@'));
                broker = b.substring(b.indexOf('@')+1);
            }    
            catch (StringIndexOutOfBoundsException e)
            {
                Exception ex = new Exception("Error parsing broker string " + getOp(BROKER));
                ex.initCause(e);
                throw ex;
            }   
            
        }
        
        if(containsOp(BROKER_OPTIONS))
        {
            String bOps = getOp(BROKER_OPTIONS);
            bOps = bOps.replaceAll(",", "'&");
            bOps = bOps.replaceAll("=", "='");
            broker = broker + "?" + bOps + "'";
        }
        buf.append(userPass);
        buf.append("@test/test?brokerlist='tcp://");
        buf.append(broker).append("'");
        if(containsOp(CON_OPTIONS))
        {
            String bOps = getOp(CON_OPTIONS);
            bOps = bOps.replaceAll(",", "'&");
            bOps = bOps.replaceAll("=", "='");
            buf.append("&").append(bOps).append("'");
        }
        
        Connection con = new AMQConnection(buf.toString());
        return con;
    }
    
    static class Option
    {
        private String shortForm;
        private String longForm;
        private String desc;
        private String valueLabel;
        private String defaultValue;
        private Class type;
        
        public Option(String shortForm, String longForm, String desc,
                      String valueLabel, String defaultValue, Class type)
        {
            this.shortForm = shortForm;
            this.longForm = longForm;
            this.defaultValue = defaultValue;
            this.type = type;
            this.desc = desc;
            this.valueLabel = valueLabel;
        }

        public String getShortForm()
        {
            return shortForm;
        }
        
        public String getLongForm()
        {
            return longForm;
        }
        
        public String getDefaultValue()
        {
            return defaultValue;
        }
        
        public Class getType()
        {
            return type;
        }    
        
        public String getDesc()
        {
            return desc;
        }
        
        public String getValueLabel()
        {
            return valueLabel;
        }
    }
}
