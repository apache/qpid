#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class OperationsGen < CppGen

  def initialize(chassis, outdir, amqp)
    super(outdir, amqp)
    @chassis=chassis
    @classname="AMQP_#{@chassis.caps}Operations"
  end
  
  def handler_method (m)
    gen "\nvirtual void #{m.cppname}("
    gen m.signature.join(",\n")
    gen ") = 0;\n"
  end

  def handler_classname(c) c.name.caps+"Handler"; end

  def handler_class(c)
    handlerclass=handler_classname c
    gen <<EOS
// ==================== class #{handlerclass} ====================
class #{handlerclass} : Invocable {
    // Constructors and destructors
  public:
    #{handlerclass}(){};
    virtual ~#{handlerclass}() {}
    // Protocol methods
EOS
    c.methods_on(@chassis).each { |m| handler_method(m) }
    gen <<EOS
}; // class #{handlerclass}


EOS
  end

  def handler_get(c)
    handlerclass=handler_classname c
    gen "virtual #{handlerclass}* get#{handlerclass}() = 0;\n"
  end

  def generate()
    h_file("#{@classname}.h") { 
      gen <<EOS
#include <sstream> 
#include "qpid/framing/ProtocolVersion.h"

namespace qpid {
namespace framing {

class #{@classname} {

  public:
    virtual ~#{@classname}() {}

    virtual ProtocolVersion getVersion() const = 0;

    // Include framing constant declarations
    #include "AMQP_Constants.h"

    // Inner classes
EOS
  indent { @amqp.classes.each { |c| handler_class(c) } }
  gen <<EOS

    // Method handler get methods

EOS
  indent { @amqp.classes.each { |c| handler_get(c) } }
  gen <<EOS
}; /* class #{@classname} */
}
EOS
}
  end
end

OperationsGen.new("client",ARGV[0], amqp).generate()
OperationsGen.new("server",ARGV[0], amqp).generate()

