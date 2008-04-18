package org.apache.qpidity.transport;

public abstract class MethodDelegate<C> {

${
from genutil import *

for c in composites:
  name = cname(c)
  out("    public void $(dromedary(name))(C context, $name struct) {}\n")
}
}
