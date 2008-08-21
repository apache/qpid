package $(pkg);

public enum Option {

${
from genutil import *

names = ["NONE", "SYNC", "BATCH"]
for c in composites:
  for f in c.query["field"]:
    t = resolve_type(f)
    if t["@name"] == "bit":
      names.append(scream(f["@name"]))

options = {}

for option in names:
 if not options.has_key(option):
    if options:
      out(",\n    ")
    options[option] = None
    out("$option")
}
}
