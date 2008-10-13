package org.apache.qpid.transport;

public abstract class MethodDelegate<C> {

    public abstract void handle(C context, Method method);
${
from genutil import *

for c in composites:
  name = cname(c)
  out("""
    public void $(dromedary(name))(C context, $name method) {
        handle(context, method);
    }""")
}
}
