package org.apache.qpid.transport;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */


import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.transport.codec.Decoder;
import org.apache.qpid.transport.codec.Encoder;

${
from genutil import *

cls = klass(type)["@name"]

segments = type["segments"]

connectioncontrol="false"

if type.name in ("control", "command"):
  base = "Method"
  size = 0
  pack = 2
  if segments:
    payload = "true"
  else:
    payload = "false"
  if type.name == "control" and cls == "connection":
    track = "Frame.L1"
    connectioncontrol="true"
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

if segments:
  out("import java.nio.ByteBuffer;\n")
  out("import org.apache.qpid.util.Strings;\n")

if track != "-1":
  out("import org.apache.qpid.transport.network.Frame;\n")
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

    public final boolean isConnectionControl()
    {
        return $connectioncontrol;
    }

${

if pack > 0:
  out("    private $(PACK_TYPES[pack]) packing_flags = 0;\n");

fields = get_fields(type)
params = get_parameters(type, fields)
options = get_options(fields)

for f in fields:
  if not f.empty:
    out("    private $(f.type) $(f.name);\n")

if segments:
  out("    private Header header;\n")
  out("    private ByteBuffer body;\n")
}

${
if fields:
  out("    public $name() {}\n")
}

    public $name($(", ".join(params))) {
${
for f in fields:
  if f.option: continue
  if f.ref_type != f.type:
    out("        $(f.set)($(f.name));\n")
  else:
    out("        if($(f.name) != null) {\n")
    out("            $(f.set)($(f.name));\n")
    out("        }\n")

if segments:
  out("        setHeader(header);\n")
  out("        setBody(body);\n")

if options or base == "Method":
  out("""
        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
""")

  for f in options:
    out("            case $(f.option): packing_flags |= $(f.flag_mask(pack)); break;\n")

  if base == "Method":
    out("""            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
""")
  out("""            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }
""")
}
    }

${

if base == "Method":
  out("""    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.$(dromedary(name))(context, this);
    }""")
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
        setDirty(true);
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
${
if not f.empty:
  out("        this.$(f.name) = value;")
}
${
if pack > 0:
  if f.empty:
    out("        if (value)\\n")
    out("        {\\n")
    out("            packing_flags |= $(f.flag_mask(pack));\\n")
    out("        }\\n")
    out("        else\\n")
    out("        {\\n")
    out("            packing_flags &= ~$(f.flag_mask(pack));\\n")
    out("        }\\n")
  else:
    out("        packing_flags |= $(f.flag_mask(pack));")
}
        setDirty(true);
        return this;
    }

    public final $name $(f.name)($(f.type) value) {
        return $(f.set)(value);
    }
""")
}

${
if segments:
    out("""    public final Header getHeader() {
        return this.header;
    }

    public final void setHeader(Header header) {
        this.header = header;
    }

    public final $name header(Header header) {
        setHeader(header);
        return this;
    }

    public int getBodySize()
    {
        return this.body == null ? 0 : this.body.remaining();
    }

    public final ByteBuffer getBody() {
        if (this.body == null)
        {
            return null;
        }
        else
        {
            return this.body.slice();
        }
    }

    public final void setBody(ByteBuffer body) {
        this.body = body;
    }

    public final $name body(ByteBuffer body)
    {
        setBody(body);
        return this;
    }

    public final byte[] getBodyBytes() {
        ByteBuffer buf = getBody();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }

    public final void setBody(byte[] body)
    {
        setBody(ByteBuffer.wrap(body));
    }

    public final String getBodyString() {
        return Strings.fromUTF8(getBodyBytes());
    }

    public final void setBody(String body) {
        setBody(Strings.toUTF8(body));
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
    out("    {\n    ")
  pre = ""
  post = ""
  if f.type_node.name == "struct":
    pre = "%s.TYPE, " % cname(f.type_node)
  elif f.type_node.name == "domain":
    post = ".getValue()"
  out("        enc.write$(f.coder)($(pre)this.$(f.name)$(post));\n")
  if pack > 0:
    out("        }\n")
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
    out("    {\n    ")
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
  if pack > 0:
    out("        }\n")
}
    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

${
for f in fields:
  if pack > 0:
    out("        if ((packing_flags & $(f.flag_mask(pack))) != 0)\n    ")
    out("    {\n    ")
  out('        result.put("$(f.name)", $(f.get)());\n')
  if pack > 0:
    out("        }\n")
}

        return result;
    }

${
if name == "ReplyTo":
  out("    public boolean equals(final Object obj){\n")
  out("        if (this == obj){\n")
  out("            return true;\n")
  out("        }\n\n")
  out("        if(!(obj instanceof ReplyTo)){\n")
  out("            return false;\n")
  out("        }\n\n")
  out("        final ReplyTo reply = (ReplyTo) obj;\n")
  out("        return (routingKey == null ? reply.getRoutingKey() == null : routingKey.equals(reply.getRoutingKey()))\n")
  out("            && (exchange == null ? reply.getExchange() == null : exchange.equals(reply.getExchange()));\n")
  out("    }\n\n")
  out("    public int hashCode(){\n")
  out("        int result = routingKey == null ? 1 : routingKey.hashCode();\n")
  out("        return 31 * result + (exchange == null ? 5 : exchange.hashCode());\n")
  out("    }")
}
}
