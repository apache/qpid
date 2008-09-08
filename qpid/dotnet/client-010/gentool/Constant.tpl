namespace org.apache.qpid.transport
{

${from genutil import *}

public class Constant
{
${
constants = spec.query["amqp/constant"]

for c in constants:
  name = scream(c["@name"])
  value = c["@value"]
  out("    public const int $name = $value;\n")
}}
}