package org.apache.qpid.transport;

${from genutil import *}

public interface Constant
{
${
constants = spec.query["amqp/constant"]

for c in constants:
  name = scream(c["@name"])
  value = c["@value"]
  out("    public static final int $name = $value;\n")
}}
