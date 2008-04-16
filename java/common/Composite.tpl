package org.apache.qpidity.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.codec.Decoder;
import org.apache.qpidity.transport.codec.Encodable;
import org.apache.qpidity.transport.codec.Encoder;

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
  pack = type["@pack"]
  payload = "false"
  track = "-1"

typecode = code(type)
}

public class $name extends $base {

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

    private static final List<Field<?,?>> FIELDS = new ArrayList<Field<?,?>>();
    public List<Field<?,?>> getFields() { return FIELDS; }

${
fields = get_fields(type)
params = get_parameters(fields)
options = get_options(fields)

for f in fields:
  out("    private boolean has_$(f.name);\n")
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

for f in options:
  out("        boolean _$(f.name) = false;\n")

if options:
  out("""
        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
""")

  for f in options:
    out("            case $(f.option): _$(f.name) = true; break;\n")

  out("""            case NO_OPTION: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }
""")

for f in options:
  out("        $(f.set)(_$(f.name));\n")
}
    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.$(dromedary(name))(context, this);
    }

${
for f in fields:
  out("""
    public final boolean $(f.has)() {
        return has_$(f.name);
    }

    public final $name $(f.clear)() {
        this.has_$(f.name) = false;
        this.$(f.name) = $(f.default);
        this.dirty = true;
        return this;
    }

    public final $(f.type) $(f.get)() {
        return $(f.name);
    }

    public final $name $(f.set)($(f.type) value) {
        this.$(f.name) = value;
        this.has_$(f.name) = true;
        this.dirty = true;
        return this;
    }

    public final $name $(f.name)($(f.type) value) {
        this.$(f.name) = value;
        this.has_$(f.name) = true;
        this.dirty = true;
        return this;
    }

    static {
        FIELDS.add(new Field<$name,$(jref(jclass(f.type)))>($name.class, $(jref(jclass(f.type))).class, "$(f.name)", $(f.index)) {
            public boolean has(Object struct) {
                return check(struct).has_$(f.name);
            }
            public void has(Object struct, boolean value) {
                check(struct).has_$(f.name) = value;
            }
            public $(jref(f.type)) get(Object struct) {
                return check(struct).$(f.get)();
            }
            public void read(Decoder dec, Object struct) {
                check(struct).$(f.name) = $(f.read);
                check(struct).dirty = true;
            }
            public void write(Encoder enc, Object struct) {
               $(f.write);
            }
        });
    }
""")
}}
