package org.apache.qpid.transport;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class $(invoker) {
${
from genutil import *

results = False

for c in composites:
  name = cname(c)
  fields = get_fields(c)
  params = get_parameters(c, fields)
  args = get_arguments(c, fields)
  result = c["result"]
  if result:
    results = True
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

  if c.name == "command":
    access = "public "
  else:
    access = ""

  out("""
    $(access)final $jresult $(dromedary(name))($(", ".join(params))) {
        $(jreturn)invoke(new $name($(", ".join(args)))$jclass);
    }
""")
}
    protected abstract void invoke(Method method);
${
if results:
  out("""
    protected abstract <T> Future<T> invoke(Method method, Class<T> resultClass);
""")
}
}
