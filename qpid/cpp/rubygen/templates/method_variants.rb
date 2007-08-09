#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

# Generate the full AMQP class/method model as C++ types.
class AmqpCppModelGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
  end

  def gen_set_variant(varname, idtype, pairs, errmsg)
    pairs.sort!
    scope("inline void setVariant(#{varname}& var, #{idtype} id) {") {
      scope("switch (id) {") {
        pairs.each { |i,t| 
          genl "case #{i}: var=#{t}(); break;";
        }
        genl "default: THROW_QPID_ERROR(FRAMING_ERROR, (boost::format(\"#{errmsg}\")%id).str());"
      }
    }
    genl
  end

  def gen_class(c)
    varname="#{c.name.caps}Variant"
    mtypes=c.amqp_methods.map { |m| m.body_name }
    typedef(blank_variant(mtypes), varname)
    genl
    pairs=c.amqp_methods.map { |m| [m.index.to_i,m.body_name] }
    gen_set_variant(varname, "MethodId", pairs,
                    "%d is not a valid method index in class #{c.name}")
    mtypes.each { |t|
      genl "template<> struct ClassVariant<#{t}> { typedef #{varname} value; };"
    }
    genl
  end

  def gen_all()
    varname="MethodVariant"
    types=@amqp.amqp_classes.map { |c| "#{c.name.caps}Variant" }
    pairs=@amqp.amqp_classes.map { |c| [c.index.to_i,"#{c.name.caps}Variant"] }
    typedef(blank_variant(types), varname) 
    genl
    gen_set_variant(varname, "ClassId", pairs,
                    "%d is not a valid class index.")
  end

  def generate()
    h_file("qpid/framing/method_variants.h") {
      @amqp.amqp_methods.each { |m| include "qpid/framing/#{m.body_name}.h"}
      include "qpid/framing/amqp_types.h"
      include "qpid/QpidError.h"
      include "qpid/framing/variant.h"
      include "<boost/format.hpp>"
      genl
      namespace("qpid::framing") {
        genl "// Metafunction returns class variant containing method T."
        genl "template <class T> struct ClassVariant {};"
        genl
        @amqp.amqp_classes.each { |c| gen_class c }
      }
      namespace("qpid::framing") {
        gen_all
        genl
      }
    }
  end
end

AmqpCppModelGen.new(Outdir, Amqp).generate();

