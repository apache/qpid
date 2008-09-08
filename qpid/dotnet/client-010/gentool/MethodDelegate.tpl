namespace org.apache.qpid.transport
{

public abstract class MethodDelegate<C> {

${
from genutil import *

for c in composites:
  name = cname(c)
  out("    public virtual void $(dromedary(name))(C context, $name mystruct) {}\n")
}
}
}