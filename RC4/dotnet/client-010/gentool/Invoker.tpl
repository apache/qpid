using System;
using System.Collections.Generic;
using System.IO;
using common.org.apache.qpid.transport.util;

namespace org.apache.qpid.transport
{

public abstract class Invoker {

    protected abstract void invoke(Method method);
    public abstract Future invoke(Method method, Future resultClass);

${
from dotnetgenutil import *

for c in composites:
  name = cname(c)
  fields = get_fields(c)
  params = get_dotnetparameters(c, fields)
  args = get_arguments(c, fields)
  result = c["result"]
  if result:
    if not result["@type"]:
      rname = cname(result["struct"])
    else:
      rname = cname(result, "@type")
    jresult = "Future" 
    jreturn = "return "
    jclass = ", new ResultFuture()" 
    jinvoke = "invoke"
  else:
    jinvoke = "invoke"
    jresult = "void"
    jreturn = ""
    jclass = ""

  out("""
    public $jresult $(dromedary(name))($(", ".join(params))) {
        $(jreturn)$jinvoke(new $name($(", ".join(args)))$jclass);
    }
""")
}

}
}