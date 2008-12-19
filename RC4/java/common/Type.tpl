package org.apache.qpid.transport;

${from genutil import *}

public enum Type
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

  if first:
    first = False
  else:
    out(",\n")

  out("    $name((byte) $code, $width, $fixed)")
};

    public byte code;
    public int width;
    public boolean fixed;

    Type(byte code, int width, boolean fixed)
    {
        this.code = code;
        this.width = width;
        this.fixed = fixed;
    }

    public static Type get(byte code)
    {
        switch (code)
        {
${
keys = list(codes.keys())
keys.sort()

for code in keys:
  out("        case (byte) $code: return $(codes[code]);\n")
}
        default: return null;
        }
    }
}
