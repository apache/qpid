#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class SessionGen < CppGen

  def initialize(outdir, amqp)
    super(outdir, amqp)
    @chassis="server"
    @classname="Session"
  end
  
  def return_type(m)
    if (m.result)
      return "TypedResult<qpid::framing::#{m.result.cpptype.ret}>"
    elsif (not m.responses().empty?)
      return "Response"
    else
      return "Completion"
    end 
  end

  def declare_method (m)
    param_unpackers = m.fields.collect { |f| "args[#{f.cppname}|#{f.cpptype.default_value}]" }
    if (m.content())
      param_names = m.param_names + ["content"]
      param_unpackers << "args[content|DefaultContent(\"\")]"
      params=m.signature + ["const MethodContent& content"]
    else
      param_names = m.param_names
      params=m.signature
    end

    if (params.empty?)
      gen "#{return_type(m)} #{m.parent.name.lcaps}#{m.name.caps}();\n\n" 
    else
      genl "template <class ArgumentPack> #{return_type(m)} #{m.parent.name.lcaps}#{m.name.caps}(ArgumentPack const& args)" 
      genl "{"
      indent {
        genl "return #{m.parent.name.lcaps}#{m.name.caps}(#{param_unpackers.join(",\n")});"
      }
      genl "}"

      #generate the 'real' methods signature
      gen "#{return_type(m)} #{m.parent.name.lcaps}#{m.name.caps}(" 
      indent { gen params.join(",\n") }
      gen ");\n\n"

      #generate some overloaded methods to handle keyword args
      boost_max_arity = 8
      if param_names.length > boost_max_arity
        keywords = param_names[1..boost_max_arity].collect { |p| "keyword::#{p}" }
      else
        keywords = param_names.collect { |p| "keyword::#{p}" }
      end
      genl "typedef boost::parameter::parameters< #{keywords.join(",")} > #{m.parent.name.caps}#{m.name.caps}Params;\n" 

      j = 1
      while j <= params.length && j <= boost_max_arity       
        dummy_args = Array.new(j) { |i| "P#{i} const& p#{i}"} 
        dummy_names = Array.new(j) { |i| "p#{i}"} 
        dummy_types = Array.new(j) { |i| "class P#{i}"} 
        
        genl "template <#{dummy_types.join(',')}> #{return_type(m)} #{m.parent.name.lcaps}#{m.name.caps}_(#{dummy_args.join(',')})"
        genl "{"
        indent { 
          genl "return #{m.parent.name.lcaps}#{m.name.caps}(#{m.parent.name.caps}#{m.name.caps}Params()(#{dummy_names.join(',')}));" 
        }
        genl "}"
        j = j + 1
      end      
    end

  end

  def define_method (m)
    if (m.content())
      params=m.signature + ["const MethodContent& content"]
    else
      params=m.signature
    end
    if (params.empty?)
      gen "#{return_type(m)} Session::#{m.parent.name.lcaps}#{m.name.caps}(){\n\n" 
    else
      gen "#{return_type(m)} Session::#{m.parent.name.lcaps}#{m.name.caps}(" 
      indent { gen params.join(",\n") }
      gen "){\n\n"
    end
    indent (2) { 
      gen "return #{return_type(m)}(impl()->send(#{m.body_name}(" 
      params = ["version"] + m.param_names
      gen params.join(", ")
      other_params=[]
      if (m.content())
        gen "), content), impl());\n"
      else
        gen ")), impl());\n"
      end
    }
    gen "}\n\n"
  end

  def declare_keywords(classes)
    #need to assemble a listof all the field names
    keywords = classes.collect { |c| c.methods_on(@chassis).collect { |m| m.param_names } }.flatten().uniq()
    keywords.each { |k| genl "BOOST_PARAMETER_KEYWORD(keyword, #{k})" }
    genl "BOOST_PARAMETER_KEYWORD(keyword, content)"
  end

  def declare_class(c)
    c.methods_on(@chassis).each { |m| declare_method(m) }
  end

  def define_class(c)
    c.methods_on(@chassis).each { |m| define_method(m) }
  end

  def generate()
    excludes = ["channel", "connection", "session", "execution"]

    h_file("qpid/client/#{@classname}.h") { 
      genl "#define BOOST_PARAMETER_MAX_ARITY 8"

      gen <<EOS
#include <sstream> 
#include <boost/parameter.hpp>
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/amqp_structs.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Response.h"
#include "qpid/client/ScopedAssociation.h"
#include "qpid/client/TypedResult.h"

namespace qpid {
namespace client {

using std::string;
using framing::Content;
using framing::FieldTable;
using framing::MethodContent;
using framing::SequenceNumberSet;

EOS
      declare_keywords(@amqp.classes.select { |c| !excludes.include?(c.name)  })
      genl 
      gen <<EOS
class #{@classname} {
  ScopedAssociation::shared_ptr assoc;
  framing::ProtocolVersion version;
  
  SessionCore::shared_ptr impl();

public:
    #{@classname}();
    #{@classname}(ScopedAssociation::shared_ptr);

    framing::FrameSet::shared_ptr get() { return impl()->get(); }
    void setSynchronous(bool sync) { impl()->setSync(sync); } 
    void close();
    Execution& execution() { return impl()->getExecution(); }

    typedef framing::TransferContent DefaultContent;
EOS
  indent { @amqp.classes.each { |c| declare_class(c) if !excludes.include?(c.name) } }
  gen <<EOS
}; /* class #{@classname} */
}
}
EOS
}

  # .cpp file
  cpp_file("qpid/client/#{@classname}.cpp") { 
    gen <<EOS
#include "#{@classname}.h"
#include "qpid/framing/all_method_bodies.h"

using std::string;
using namespace qpid::framing;

namespace qpid {
namespace client {

#{@classname}::#{@classname}() {}
#{@classname}::#{@classname}(ScopedAssociation::shared_ptr _assoc) : assoc(_assoc) {}

SessionCore::shared_ptr #{@classname}::impl()
{
    if (!assoc) throw Exception("Uninitialised session");
    return assoc->session;
}

void #{@classname}::close()
{
    impl()->close(); 
}

EOS

  @amqp.classes.each { |c| define_class(c) if !excludes.include?(c.name)  }
  
  gen <<EOS
}} // namespace qpid::client
EOS
  }

  end
end

SessionGen.new(ARGV[0], Amqp).generate()

