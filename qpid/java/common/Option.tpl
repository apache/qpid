package org.apache.qpid.transport;

public enum Option {

${
from genutil import *

options = {}

for c in composites:
  for f in c.query["field"]:
    t = resolve_type(f)
    if t["@name"] == "bit":
      option = scream(f["@name"])
      if not options.has_key(option):
        options[option] = None
        out("    $option,\n")}
    BATCH,
    NONE
}
