package org.apache.qpidity.transport;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class Invoker {

    protected abstract void invoke(Method method);
    protected abstract <T> Future<T> invoke(Method method, Class<T> resultClass);

${
from genutil import *

for c in composites:
  name = cname(c)
  fields = get_fields(c)
  params = get_parameters(fields)
  args = get_arguments(fields)
  result = c["result"]
  if result:
    if not result["@type"]:
      rname = cname(result["struct"])
    else:
      rname = cname(result, "@type")
    jresult = "Future<%s>" % rname
    jreturn = "return "
    jclass = ", %s.class" % rname
  else:
    jresult = "void"
    jreturn = ""
    jclass = ""

  out("""
     public $jresult $(dromedary(name))($(", ".join(params))) {
         $(jreturn)invoke(new $name($(", ".join(args)))$jclass);
     }
""")
}

}
