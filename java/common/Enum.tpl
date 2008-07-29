package org.apache.qpid.transport;

public enum $name {
${
from genutil import *

vtype = jtype(resolve_type(type))

choices = [(scream(ch["@name"]), "(%s) %s" % (vtype, ch["@value"]))
           for ch in type.query["enum/choice"]]
}
    $(",\n    ".join(["%s(%s)" % ch for ch in choices]));

    private final $vtype value;

    $name($vtype value)
    {
        this.value = value;
    }

    public $vtype getValue()
    {
        return value;
    }

    public static $name get($vtype value)
    {
        switch (value)
        {
${
for ch, value in choices:
  out('        case $value: return $ch;\n')
}        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
