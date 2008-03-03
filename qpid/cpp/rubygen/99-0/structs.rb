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
    "mediumstr"=>"MediumString",
    "timestamp"=>"LongLong",
    "table"=>"FieldTable",
    "content"=>"Content",
    "long-struct"=>"LongString"
  }
  SizeMap={
    "octet"=>1,
    "short"=>2,
    "long"=>4,
    "longlong"=>8,
    "timestamp"=>8
  }

  ValueTypes=["octet", "short", "long", "longlong", "timestamp"]

  def is_packed(s)
    #return true
    s.kind_of?(AmqpStruct) or s.body_name.include?("010")
  end

  def execution_header?(s)
    false and s.kind_of? AmqpMethod and s.parent.l4?
  end

  def has_bitfields_only(s)
    s.fields.select {|f| f.domain.type_ != "bit"}.empty?
  end

  def default_initialisation(s)
    params = s.fields.select {|f| ValueTypes.include?(f.domain.type_) || (!is_packed(s) && f.domain.type_ == "bit")}
    strings = params.collect {|f| "#{f.cppname}(0)"}   
    strings << "flags(0)" if (is_packed(s))
    if strings.empty?
      return ""
    else
      return " : " + strings.join(", ")
    end
  end

  def printable_form(f)
    if (f.cpptype.name == "uint8_t")
      return "(int) " + f.cppname
    elsif (f.domain.type_ == "bit")
      return "get#{f.name.caps}()"
    else
      return f.cppname
    end
  end

  def flag_mask(s, i)
    pos = SizeMap[s.pack]*8 - 8 - (i/8)*8 + (i % 8)
    return "(1 << #{pos})"
  end

  def encode_packed_struct(s)
    genl s.cpp_pack_type.encode('flags', 'buffer')
    process_packed_fields(s) { |f, i| encode_packed_field(s, f, i) unless f.domain.type_ == "bit" }
  end

  def decode_packed_struct(s)
    genl "#{s.cpp_pack_type.decode('flags', 'buffer')}"
    process_packed_fields(s) { |f, i| decode_packed_field(s, f, i) unless f.domain.type_ == "bit" }
  end

  def size_packed_struct(s)
    genl "total += #{SizeMap[s.pack]};"
    process_packed_fields(s) { |f, i| size_packed_field(s, f, i) unless f.domain.type_ == "bit" }
  end

  def print_packed_struct(s)
    process_packed_fields(s) { |f, i| print_packed_field(s, f, i) }
  end

  def encode_packed_field(s, f, i)
    genl "if (flags & #{flag_mask(s, i)})"
    indent { genl f.domain.cpptype.encode(f.cppname,"buffer") }
  end

  def decode_packed_field(s, f, i)
    genl "if (flags & #{flag_mask(s, i)})"
    indent { genl f.domain.cpptype.decode(f.cppname,"buffer") }
  end

  def size_packed_field(s, f, i)
      genl "if (flags & #{flag_mask(s, i)})"
      indent { generate_size(f, []) }
  end

  def print_packed_field(s, f, i)
    genl "if (flags & #{flag_mask(s, i)})"
    indent { 
      genl "out << \"#{f.name}=\" << #{printable_form(f)} << \"; \";"
    }
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
      genl "total += 1;//#{names.join(", ")}"
    else
      size = SizeMap[f.domain.type_]
      if (size)
        genl "total += #{size};//#{f.cppname}"
      elsif (f.cpptype.name == "SequenceNumberSet")
        genl "total += #{f.cppname}.encodedSize();"
      else 
        encoded = EncodingMap[f.domain.type_]        
        gen "total += ("
        gen "4 + " if encoded == "LongString"
        gen "1 + " if encoded == "ShortString"
        genl "#{f.cppname}.size());"
      end
    end
  end

  def process_packed_fields(s)
    s.fields.each { |f| yield f, s.fields.index(f) }
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

  def all_fields_via_accessors(s)
    s.fields.collect { |f| "get#{f.name.caps}()" }.join(", ")
  end

  def methodbody_extra_defs(s)
    if (s.parent.control?) 
      genl "virtual uint8_t type() const { return 0;/*control segment*/ }"
    end
    

    gen <<EOS
    typedef #{s.result ? s.result.struct.cpptype.name : 'void'} ResultType;

    template <class T> ResultType invoke(T& invocable) const {
        return invocable.#{s.cppname}(#{all_fields_via_accessors(s)});
    }

    using  AMQMethodBody::accept;
    void accept(MethodBodyConstVisitor& v) const { v.visit(*this); }

    ClassId amqpClassId() const { return CLASS_ID; }
    MethodId amqpMethodId() const { return METHOD_ID; }
    bool isContentBearing() const { return  #{s.content ? "true" : "false" }; }
    bool resultExpected() const { return  #{s.result ? "true" : "false"}; }
    bool responseExpected() const { return  #{s.responses().empty? ? "false" : "true"}; }
EOS
  end

  def define_constructor(name, s)
    if (s.fields.size > 0)
      genl "#{name}("
      if (s.kind_of? AmqpMethod)
        indent {gen "ProtocolVersion, "}
      end
      indent { gen s.fields.collect { |f| "#{f.cpptype.param} _#{f.cppname}" }.join(",\n") }
      genl ") : "
      if (is_packed(s))
        initialisers = s.fields.select { |f| f.domain.type_ != "bit"}.collect { |f| "#{f.cppname}(_#{f.cppname})"}

        initialisers << "flags(0)"
        indent { gen initialisers.join(",\n") }
        genl "{"
        indent {
          process_packed_fields(s) { |f, i| genl "set#{f.name.caps}(_#{f.cppname});" if f.domain.type_ == "bit"}
          process_packed_fields(s) { |f, i| genl "flags |= #{flag_mask(s, i)};" unless f.domain.type_ == "bit"}
        }
        genl "}"          
      else
        indent { gen s.fields.collect { |f| " #{f.cppname}(_#{f.cppname})" }.join(",\n") }
        genl "{}"
      end
    end
    #default constructors:
    if (s.kind_of? AmqpMethod)
      genl "#{name}(ProtocolVersion=ProtocolVersion()) {}"
    end
    if (s.kind_of? AmqpStruct)
      genl "#{name}() #{default_initialisation(s)} {}"
    end
  end

  def define_packed_field_accessors(s, f, i)
    if (s.kind_of? AmqpMethod) 
      define_packed_field_accessors_for_method(s, f, i)
    else
      define_packed_field_accessors_for_struct(s, f, i)
    end
  end

  def define_packed_field_accessors_for_struct(s, f, i)
    if (f.domain.type_ == "bit")
      genl "void #{s.cppname}::set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname}) {"
      indent {
        genl "if (_#{f.cppname}) flags |= #{flag_mask(s, i)};"
        genl "else flags &= ~#{flag_mask(s, i)};"
      }
      genl "}"
      genl "#{f.cpptype.ret} #{s.cppname}::get#{f.name.caps}() const { return flags & #{flag_mask(s, i)}; }"
    else 
      genl "void #{s.cppname}::set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname}) {"
      indent {
        genl "#{f.cppname} = _#{f.cppname};"
        genl "flags |= #{flag_mask(s, i)};"
      }
      genl "}"
      genl "#{f.cpptype.ret} #{s.cppname}::get#{f.name.caps}() const { return #{f.cppname}; }"
      if (f.cpptype.name == "FieldTable")
        genl "#{f.cpptype.name}& #{s.cppname}::get#{f.name.caps}() {"
        indent { 
          genl "flags |= #{flag_mask(s, i)};"#treat the field table as having been 'set'
          genl "return #{f.cppname};" 
        }
        genl "}"
      end
      genl "bool #{s.cppname}::has#{f.name.caps}() const { return flags & #{flag_mask(s, i)}; }"
      genl "void #{s.cppname}::clear#{f.name.caps}Flag() { flags &= ~#{flag_mask(s, i)}; }"
    end
    genl ""
  end

  def define_packed_field_accessors_for_method(s, f, i)
    if (f.domain.type_ == "bit")
      genl "void #{s.body_name}::set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname}) {"
      indent {
        genl "if (_#{f.cppname}) flags |= #{flag_mask(s, i)};"
        genl "else flags &= ~#{flag_mask(s, i)};"
      }
      genl "}"
      genl "#{f.cpptype.ret} #{s.body_name}::get#{f.name.caps}() const { return flags & #{flag_mask(s, i)}; }"
    else 
      genl "void #{s.body_name}::set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname}) {"
      indent {
        genl "#{f.cppname} = _#{f.cppname};"
        genl "flags |= #{flag_mask(s, i)};"
      }
      genl "}"
      genl "#{f.cpptype.ret} #{s.body_name}::get#{f.name.caps}() const { return #{f.cppname}; }"
      if (f.cpptype.name == "FieldTable")
        genl "#{f.cpptype.name}& #{s.body_name}::get#{f.name.caps}() {"
        indent { 
          genl "flags |= #{flag_mask(s, i)};"#treat the field table as having been 'set'
          genl "return #{f.cppname};" 
        }
        genl "}"
      end
      genl "bool #{s.body_name}::has#{f.name.caps}() const { return flags & #{flag_mask(s, i)}; }"
      genl "void #{s.body_name}::clear#{f.name.caps}Flag() { flags &= ~#{flag_mask(s, i)}; }"
    end
    genl ""
  end

  def define_packed_accessors(s)
    process_packed_fields(s) { |f, i| define_packed_field_accessors(s, f, i) }
  end

  def declare_packed_accessors(f)
    genl "void set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname});";
    genl "#{f.cpptype.ret} get#{f.name.caps}() const;"
    if (f.cpptype.name == "FieldTable")
      genl "#{f.cpptype.name}& get#{f.name.caps}();"
    end
    if (f.domain.type_ != "bit")
      #extra 'accessors' for packed fields:
      genl "bool has#{f.name.caps}() const;"
      genl "void clear#{f.name.caps}Flag();"
    end
  end

  def define_accessors(f)
    genl "void set#{f.name.caps}(#{f.cpptype.param} _#{f.cppname}) { #{f.cppname} = _#{f.cppname}; }"
    genl "#{f.cpptype.ret} get#{f.name.caps}() const { return #{f.cppname}; }"
    if (f.cpptype.name == "FieldTable")
      genl "#{f.cpptype.name}& get#{f.name.caps}() { return #{f.cppname}; }"
    end
  end

  def define_struct(s)
    classname = s.cppname
    inheritance = ""
    if (s.kind_of? AmqpMethod)
      classname = s.body_name
      if (execution_header?(s))
        inheritance = ": public ModelMethod"
      else
        inheritance = ": public AMQMethodBody"
      end
    end

    h_file("qpid/framing/#{classname}.h") { 
      if (s.kind_of? AmqpMethod)
        gen <<EOS
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/MethodBodyConstVisitor.h"
EOS
      end
      include "qpid/framing/ModelMethod.h" if (execution_header?(s))

      #need to include any nested struct definitions
      s.fields.each { |f| include f.cpptype.name if f.domain.struct }

      gen <<EOS

#include <ostream>
#include "qpid/framing/amqp_types_full.h"

namespace qpid {
namespace framing {

class #{classname} #{inheritance} {
EOS
  if (is_packed(s))
    indent { s.fields.each { |f| genl "#{f.cpptype.name} #{f.cppname};" unless f.domain.type_ == "bit"} }
    indent {
      genl "#{s.cpp_pack_type.name} flags;"
    }
  else
    indent { s.fields.each { |f| genl "#{f.cpptype.name} #{f.cppname};" } }
  end
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
        #solution is expected from the WG)
        indent { genl "static const uint16_t TYPE = #{s.type_} + #{s.parent.parent.parent.index} * 256;" }
      else
        indent { genl "static const uint16_t TYPE = #{s.type_};" } 
      end
    end
  end

  indent { 
    define_constructor(classname, s)
    genl ""
    if (is_packed(s))
      s.fields.each { |f| declare_packed_accessors(f) } 
    else
      s.fields.each { |f| define_accessors(f) } 
    end
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
    void encodeStructBody(Buffer&) const;
    void decodeStructBody(Buffer&, uint32_t=0);
    uint32_t size() const;
    uint32_t bodySize() const;
    void print(std::ostream& out) const;
}; /* class #{classname} */

}}
EOS
    }
    cpp_file("qpid/framing/#{classname}.cpp") { 
      if (is_packed(s) || s.fields.size > 0 || execution_header?(s))
        buffer = "buffer"
      else
        buffer = "/*buffer*/"
      end
      gen <<EOS
#include "#{classname}.h"

using namespace qpid::framing;

EOS
    
      if (is_packed(s))
        define_packed_accessors(s)
      end
      gen <<EOS
void #{classname}::encodeStructBody(Buffer& #{buffer}) const
{
EOS
      if (execution_header?(s))
        genl "ModelMethod::encode(buffer);"
      end

      if (is_packed(s))
        indent {encode_packed_struct(s)}
      else 
        indent { process_fields(s) { |f, combined| generate_encode(f, combined) } } 
      end
      gen <<EOS
}

void #{classname}::encode(Buffer& buffer) const
{
EOS
      indent {
        if (s.kind_of? AmqpStruct)
          if (s.type_)
            genl "buffer.put#{s.size.caps}(bodySize() + 2/*typecode*/);" if s.size
            genl "buffer.putShort(TYPE);" 
          else
            genl "buffer.put#{s.size.caps}(size());" if s.size
          end
        end
        genl "encodeStructBody(buffer);"
      }
      gen <<EOS
}

void #{classname}::decodeStructBody(Buffer& #{buffer}, uint32_t /*size*/)
{
EOS
      if (execution_header?(s))
        genl "ModelMethod::decode(buffer);"
      end

      if (is_packed(s))
        indent {decode_packed_struct(s)}
      else 
        indent { process_fields(s) { |f, combined| generate_decode(f, combined) } } 
      end
      gen <<EOS
}

void #{classname}::decode(Buffer& buffer, uint32_t /*size*/)
{
EOS
      indent {
        if (s.kind_of? AmqpStruct)
          genl "buffer.get#{s.size.caps}();" if s.size
          genl "if (TYPE != buffer.getShort()) throw InternalErrorException(\"Bad type code for struct\");" if s.type_
        end
        genl "decodeStructBody(buffer);"
      }
      gen <<EOS
}

uint32_t #{classname}::bodySize() const
{
    uint32_t total = 0;
EOS
      if (execution_header?(s))
        genl "total += ModelMethod::size();"
      end

      if (is_packed(s))
        indent {size_packed_struct(s)}
      else 
        indent { process_fields(s) { |f, combined| generate_size(f, combined) } } 
      end
      gen <<EOS
    return total;
}

uint32_t #{classname}::size() const
{
    uint32_t total = bodySize();
EOS
        if (s.kind_of? AmqpStruct)
          genl "total += #{SizeMap[s.size]}/*size field*/;" if s.size
          genl "total += 2/*typecode*/;" if s.type_
        end
      gen <<EOS
    return total;
}

void #{classname}::print(std::ostream& out) const
{
    out << "{#{classname}: ";
EOS
      if (is_packed(s))
        indent {print_packed_struct(s)}
      else 
        copy = Array.new(s.fields)
        f = copy.shift
        
        indent { 
          genl "out << \"#{f.name}=\" << #{printable_form(f)};" if f
          copy.each { |f| genl "out << \"; #{f.name}=\" << #{printable_form(f)};" } 
        } 
      end
      gen <<EOS
    out << "}";
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

StructGen.new(ARGV[0], $amqp).generate()

