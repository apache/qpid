package org.apache.qpid.transport;

class StructFactory {

    public static Struct create(int type)
    {
        switch (type)
        {
${
from genutil import *

fragment = """        case $name.TYPE:
            return new $name();
"""

for c in composites:
  name = cname(c)
  if c.name == "struct":
    out(fragment)
}        default:
            throw new IllegalArgumentException("type: " + type);
        }
    }

    public static Struct createInstruction(int type)
    {
        switch (type)
        {
${
for c in composites:
  name = cname(c)
  if c.name in ("command", "control"):
    out(fragment)
}        default:
            throw new IllegalArgumentException("type: " + type);
        }
    }

}
