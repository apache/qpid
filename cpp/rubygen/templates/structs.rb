#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class StructGen < CppGen

  def initialize(outdir, amqp)
    super(outdir, amqp)
  end

  EncodingMap={
    "octet"=>"Octet",
    "short"=>"Short",
    "long"=>"Long",
    "longlong"=>"LongLong",
    "longstr"=>"LongString",
    "shortstr"=>"ShortString",
    "timestamp"=>"LongLong",
    "table"=>"FieldTable",
    "content"=>"Content",
    "long-struct"=>"LongString",
    "uuid" => "ShortString"        # FIXME aconway 2007-08-27:
  }
  SizeMap={
    "octet"=>"1",
    "short"=>"2",
    "long"=>"4",
    "longlong"=>"8",
    "timestamp"=>"8"
  }

  ValueTypes=["octet", "short", "long", "longlong", "timestamp"]

  def printable_form(f)
    if (f.cpptype.name == "u_int8_t")
      return "(int) " + f.cppname
    else
      return f.cppname
    end
  end

  def generate_encode(f, combined)
    if (f.domain.type_ == "bit")
      genl "uint8_t #{f.cppname}_bits = #{f.cppname};"
      count = 0
      combined.each { |c| genl "#{f.cppname}_bits |= #{c.cppname} << #{count += 1};" }
      genl "buffer.putOctet(#{f.cppname}_bits);"
    else
      genl f.domain.cpptype.encode(f.cppname,"buffer")
    end
  end

  def generate_decode(f, combined)
    if (f.domain.type_ == "bit")
      genl "uint8_t #{f.cppname}_bits = buffer.getOctet();"
      genl "#{f.cppname} = 1 & #{f.cppname}_bits;"
      count = 0
      combined.each { |c| genl "#{c.cppname} = (1 << #{count += 1}) & #{f.cppname}_bits;" }
    else
      genl f.domain.cpptype.decode(f.cppname,"buffer")
    end
  end

  def generate_size(f, combined)
    if (f.domain.type_ == "bit")
      names = ([f] + combined).collect {|g| g.cppname}
      genl "+ 1 //#{names.join(", ")}"
    else
      size = SizeMap[f.domain.type_]
      if (size)
        genl "+ #{size} //#{f.cppname}"
      elsif (f.cpptype.name == "SequenceNumberSet")
        genl "+ #{f.cppname}.encodedSize()"
      else 
        encoded = EncodingMap[f.domain.type_]        
        gen "+ 4 " if encoded == "LongString"
        gen "+ 1 " if encoded == "ShortString"
        genl "+ #{f.cppname}.size()"
      end
    end
  end

  def process_fields(s)
    last = nil
    count = 0  
    bits = []
    s.fields.each { 
      |f| if (last and last.bit? and f.bit? and count < 7) 
            count += 1
            bits << f
          else
            if (last and last.bit?)
              yield last, bits
              count = 0
              bits = []
            end
            if (not f.bit?)
              yield f
            end
            last = f
          end
    }
    if (last and last.bit?)
      yield last, bits
    end
  end

  def methodbody_extra_defs(s)
    gen <<EOS
    using  AMQMethodBody::accept;
    void accept(MethodBodyConstVisitor& v) const { v.visit(*this); }

    inline ClassId amqpClassId() const { return CLASS_ID; }
    inline MethodId amqpMethodId() const { return METHOD_ID; }
EOS
  end

  def define_constructor(name, s)
    if (s.fields.size > 0)
      genl "#{name}("
      if (s.kind_of? AmqpMethod)
        indent {gen "ProtocolVersion, "}
      end
      indent { gen s.fields.collect { |f| "#{f.cpptype.param} _#{f.cppname}" }.join(",\n") }
      gen ")"
      genl ": " if s.fields.size > 0
      indent { gen s.fields.collect { |f| " #{f.cppname}(_#{f.cppname})" }.join(",\n") }
      genl " {}"
    end
    if (s.kind_of? AmqpMethod)
      genl "#{name}(ProtocolVersion=ProtocolVersion()) {}"
    end
  end

  def define_accessors(f)
    genl "void set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname}) { #{f.cppname} = _#{f.cppname}; }"
    genl "#{f.cpptype.ret} get#{f.name.caps}() const { return #{f.cppname}; }"
  end

  def define_struct(s)
    classname = s.cppname
    inheritance = ""
    if (s.kind_of? AmqpMethod)
      classname = s.body_name
      inheritance = ": public AMQMethodBody"
    end

    h_file("qpid/framing/#{classname}.h") { 
      if (s.kind_of? AmqpMethod)
        gen <<EOS
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/MethodBodyConstVisitor.h"
EOS
      end

      #need to include any nested struct definitions
      s.fields.each { |f| include f.cpptype.name if f.domain.struct }

      gen <<EOS

#include <ostream>
#include "qpid/framing/amqp_types_full.h"

namespace qpid {
namespace framing {

class #{classname} #{inheritance} {
EOS
  indent { s.fields.each { |f| genl "#{f.cpptype.name} #{f.cppname};" } }
  genl "public:"
  if (s.kind_of? AmqpMethod)
    indent { genl "static const ClassId CLASS_ID = #{s.parent.index};" }
    indent { genl "static const MethodId METHOD_ID = #{s.index};" }
  end

  if (s.kind_of? AmqpStruct)
    if (s.type_)
      if (s.result?)
        #as result structs have types that are only unique to the
        #class, they have a class dependent qualifier added to them
        #(this is inline with current python code but a formal
        #solution is expected from the WG
        indent { genl "static const uint16_t TYPE = #{s.type_} + #{s.parent.parent.parent.index} * 256;" }
      else
        indent { genl "static const uint16_t TYPE = #{s.type_};" } 
      end
    end
  end

  indent { 
    define_constructor(classname, s)
    genl ""
    s.fields.each { |f| define_accessors(f) } 
  }
  if (s.kind_of? AmqpMethod)
    methodbody_extra_defs(s)
  end
  if (s.kind_of? AmqpStruct)
    indent {genl "friend std::ostream& operator<<(std::ostream&, const #{classname}&);" }
  end

  gen <<EOS
    void encode(Buffer&) const;
    void decode(Buffer&, uint32_t=0);
    uint32_t size() const;
    void print(std::ostream& out) const;
}; /* class #{classname} */

}}
EOS
    }
    cpp_file("qpid/framing/#{classname}.cpp") { 
      if (s.fields.size > 0)
        buffer = "buffer"
      else
        buffer = "/*buffer*/"
      end
      gen <<EOS
#include "#{classname}.h"

using namespace qpid::framing;

void #{classname}::encode(Buffer& #{buffer}) const
{
EOS
  indent { process_fields(s) { |f, combined| generate_encode(f, combined) } } 
  gen <<EOS
}

void #{classname}::decode(Buffer& #{buffer}, uint32_t /*size*/)
{
EOS
  indent { process_fields(s) { |f, combined| generate_decode(f, combined) } } 
  gen <<EOS
}

uint32_t #{classname}::size() const
{
    return 0
EOS
  indent { process_fields(s) { |f, combined| generate_size(f, combined) } } 
  gen <<EOS
        ;
}

void #{classname}::print(std::ostream& out) const
{
    out << "#{classname}: ";
EOS
  copy = Array.new(s.fields)
  f = copy.shift
  
  indent { 
    genl "out << \"#{f.name}=\" << #{printable_form(f)};" if f
    copy.each { |f| genl "out << \"; #{f.name}=\" << #{printable_form(f)};" } 
  } 
  gen <<EOS
}
EOS

  if (s.kind_of? AmqpStruct)
    gen <<EOS
namespace qpid{
namespace framing{

    std::ostream& operator<<(std::ostream& out, const #{classname}& s) 
    {
      s.print(out);
      return out;
    }

}
}
EOS
  end 

}
  end

  def generate()
    @amqp.structs.each { |s| define_struct(s) }
    @amqp.methods_.each { |m| define_struct(m) }
    #generate a single include file containing the list of structs for convenience
    h_file("qpid/framing/amqp_structs.h") { @amqp.structs.each { |s| genl "#include \"#{s.cppname}.h\"" } }
  end
end

StructGen.new(ARGV[0], Amqp).generate()

