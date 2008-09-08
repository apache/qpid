using System;

namespace org.apache.qpid.transport
{

${from genutil import *}

public struct QpidType
{
    public Code code;
    public int width;
    public bool isfixed;

    public Code Code
    {
        get { return code; }
        set { code = value; }
    }
    
    public int Width
    {
        get { return width; }
        set { width = value; }
    }
    
    public bool Fixed
    {
        get { return isfixed; }
        set { isfixed = value; }
    }
    
    QpidType(Code code, int width, bool isfixed)
    {
        this.code = code;
        this.width = width;
        this.isfixed = isfixed;
    }
    
    public static QpidType get(byte code)
    {
        switch (code)
        {      	
${
types = spec.query["amqp/type"] + spec.query["amqp/class/type"]
codes = {}
first = True
for t in types:
  code = t["@code"]
  fix_width = t["@fixed-width"]
  var_width = t["@variable-width"]

  if code is None:
    continue

  if fix_width is None:
    width = var_width
    fixed = "false"
  else:
    width = fix_width
    fixed = "true"

  name = scream(t["@name"])
  codes[code] = name
  
  out("          case $code : return new QpidType(Code.$name, $width, $fixed);\n")
}
          default: throw new Exception("unknown code: " + code);
        }
    }
}

public enum Code : byte
   {    
${
keys = list(codes.keys())
keys.sort()

for code in keys:
  out("   $(codes[code]) = $code,\n")
}
   }
}