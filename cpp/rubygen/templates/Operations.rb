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
    if (m.has_result?)
      return_type = "#{m.amqp_parent.name.caps}#{m.cppname.caps}Result"
    else
      return_type = "void"
    end
    if (m.amqp_parent.name == "message" && (m.name == "transfer" || m.name == "append"))
      gen "\nvirtual #{return_type} #{m.cppname}(const framing::AMQMethodBody& context) = 0;\n"
    else
      gen "\nvirtual #{return_type} #{m.cppname}("
      gen m.signature.join(",\n")
      gen ") = 0;\n"
    end
  end

  def handler_classname(c) c.name.caps+"Handler"; end

  def handler_class(c)
    if (!c.amqp_methods_on(@chassis).empty?)
      handlerclass=handler_classname c
      gen <<EOS
// ==================== class #{handlerclass} ====================
class #{handlerclass} : public virtual Invocable {
    // Constructors and destructors
  public:
    #{handlerclass}(){};
    virtual ~#{handlerclass}() {}
    // Protocol methods
EOS
      c.amqp_methods_on(@chassis).each { |m| handler_method(m) }
      gen <<EOS
}; // class #{handlerclass}


EOS
    end
  end

  def handler_get(c)
    if (!c.amqp_methods_on(@chassis).empty?)
      handlerclass=handler_classname c
      gen "virtual #{handlerclass}* get#{handlerclass}() = 0;\n"
    end
  end

  def generate()
    h_file("qpid/framing/#{@classname}.h") { 
      gen <<EOS
#include <sstream> 
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/amqp_structs.h"

namespace qpid {
namespace framing {

class AMQMethodBody;

class Invocable 
{
protected:
    Invocable() {}        
    virtual ~Invocable() {}        
};

class #{@classname} {

  public:
    virtual ~#{@classname}() {}

    virtual ProtocolVersion getVersion() const = 0;

    // Include framing constant declarations - why?
    //#include "qpid/framing/AMQP_Constants.h"

    // Inner classes
EOS
  indent { @amqp.amqp_classes.each { |c| handler_class(c) } }
  gen <<EOS

    // Method handler get methods

EOS
  indent { @amqp.amqp_classes.each { |c| handler_get(c) } }
  gen <<EOS
}; /* class #{@classname} */
}}
EOS
}
  end
end

OperationsGen.new("client",ARGV[0], Amqp).generate()
OperationsGen.new("server",ARGV[0], Amqp).generate()

