package org.apache.qpidity.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.codec.Decoder;
import org.apache.qpidity.transport.codec.Encodable;
import org.apache.qpidity.transport.codec.Encoder;
import org.apache.qpidity.transport.codec.Validator;

import org.apache.qpidity.transport.network.Frame;

${
from genutil import *

cls = klass(type)["@name"]

if type.name in ("control", "command"):
  base = "Method"
  size = 0
  pack = 2
  if type["segments"]:
    payload = "true"
  else:
    payload = "false"
  if type.name == "control" and cls == "connection":
    track = "Frame.L1"
  elif cls == "session" and type["@name"] in ("attach", "attached", "detach", "detached"):
    track = "Frame.L2"
  elif type.name == "command":
    track = "Frame.L4"
  else:
    track = "Frame.L3"
else:
  base = "Struct"
  size = type["@size"]
  pack = num(type["@pack"])
  payload = "false"
  track = "-1"

PACK_TYPES = {
  1: "byte",
  2: "short",
  4: "int"
}

typecode = code(type)
}

public final class $name extends $base {

    public static final int TYPE = $typecode;

    public final int getStructType() {
        return TYPE;
    }

    public final int getSizeWidth() {
        return $size;
    }

    public final int getPackWidth() {
        return $pack;
    }

    public final boolean hasPayload() {
        return $payload;
    }

    public final byte getEncodedTrack() {
        return $track;
    }

${

if pack > 0:
  out("    private $(PACK_TYPES[pack]) packing_flags = 0;\n");

fields = get_fields(type)
params = get_parameters(fields)
options = get_options(fields)

for f in fields:
  if not f.empty:
    out("    private $(f.type) $(f.name);\n")
}

${
if fields:
  out("    public $name() {}\n")
}

    public $name($(", ".join(params))) {
${
for f in fields:
  if f.option: continue
  out("        $(f.set)($(f.name));\n")

if options:
  out("""
        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
""")

  for f in options:
    out("            case $(f.option): packing_flags |= $(f.flag_mask(pack)); break;\n")

  out("""            case NO_OPTION: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }
""")
}
    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.$(dromedary(name))(context, this);
    }

${
for f in fields:
  if pack > 0:
    out("""
    public final boolean $(f.has)() {
        return (packing_flags & $(f.flag_mask(pack))) != 0;
    }

    public final $name $(f.clear)() {
        packing_flags &= ~$(f.flag_mask(pack));
${
if not f.empty:
  out("        this.$(f.name) = $(f.default);")
}
        this.dirty = true;
        return this;
    }
""")

  out("""
    public final $(f.type) $(f.get)() {
${
if f.empty:
  out("        return $(f.has)();")
else:
  out("        return $(f.name);")
}
    }

    public final $name $(f.set)($(f.type) value) {
        $(f.check)
${
if not f.empty:
  out("        this.$(f.name) = value;")
}
${
if pack > 0:
  out("        packing_flags |= $(f.flag_mask(pack));")
}
        this.dirty = true;
        return this;
    }

    public final $name $(f.name)($(f.type) value) {
        return $(f.set)(value);
    }
""")
}

    public void write(Encoder enc)
    {
${
if pack > 0:
  out("        enc.writeUint%s(packing_flags);\n" % (pack*8));

for f in fields:
  if f.empty:
    continue
  if pack > 0:
    out("        if ((packing_flags & $(f.flag_mask(pack))) != 0)\n    ")
  pre = ""
  post = ""
  if f.type_node.name == "struct":
    pre = "%s.TYPE, " % cname(f.type_node)
  elif f.type_node.name == "domain":
    post = ".getValue()"
  out("        enc.write$(f.coder)($(pre)this.$(f.name)$(post));\n")
}
    }

    public void read(Decoder dec)
    {
${
if pack > 0:
   out("        packing_flags = ($(PACK_TYPES[pack])) dec.readUint%s();\n" % (pack*8));

for f in fields:
  if f.empty:
    continue
  if pack > 0:
    out("        if ((packing_flags & $(f.flag_mask(pack))) != 0)\n    ")
  pre = ""
  post = ""
  arg = ""
  if f.type_node.name == "struct":
    pre = "(%s)" % cname(f.type_node)
    arg = "%s.TYPE" % cname(f.type_node)
  elif f.type_node.name == "domain":
    pre = "%s.get(" % cname(f.type_node)
    post = ")"
  out("        this.$(f.name) = $(pre)dec.read$(f.coder)($(arg))$(post);\n")
}
    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

${
for f in fields:
  if pack > 0:
    out("        if ((packing_flags & $(f.flag_mask(pack))) != 0)\n    ")
  out('        result.put("$(f.name)", $(f.get)());\n')
}

        return result;
    }

}
