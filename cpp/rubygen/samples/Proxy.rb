#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class ProxyGen < CppGen
    
  def initialize(chassis, outdir, amqp)
    super(outdir, amqp)
    @chassis=chassis
    @classname="AMQP_#{@chassis.caps}Proxy"
  end

  def include(m) gen "#include \"#{m.body_name}.h\"\n"; end

  def proxy_member(c) c.name.lcaps+"Proxy"; end
  
  def inner_class_decl(c)
    cname=c.name.caps
    gen <<EOS
    // ==================== class #{cname} ====================
    class #{cname}
    {
      private:
        ChannelAdapter& channel;

      public:
        // Constructors and destructors

        #{cname}(ChannelAdapter& ch) : 
            channel(ch) {}
        virtual ~#{cname}() {}

        static #{cname}& get(#{@classname}& proxy) { return proxy.get#{cname}();}

        // Protocol methods
EOS
    indent(2) { c.methods_on(@chassis).each { |m| inner_method_decl(m) } }
    gen "\n    }; // class #{cname}\n\n"
  end

  def inner_method_decl(m)
    gen "virtual void #{m.cppname}(#{m.signature.join(",\n            ")})\n\n";
  end

  def inner_class_defn(c)
    cname=c.cppname
    gen "// ==================== class #{cname} ====================\n"
    c.methods_on(@chassis).each { |m| inner_method_defn(m, cname) }
  end
  
  def inner_method_defn(m,cname)
    if m.response?
      rettype="void"
      ret=""
      sigadd=["RequestId responseTo"]
      paramadd=["channel.getVersion(), responseTo"]
    else
      rettype="RequestId"
      ret="return "
      sigadd=[]
      paramadd=["channel.getVersion()"]
    end
    sig=(m.signature+sigadd).join(", ")
    params=(paramadd+m.param_names).join(",\n            ")
    gen <<EOS
#{rettype} #{@classname}::#{cname}::#{m.cppname}(#{sig}) {
    #{ret}channel.send(new #{m.body_name}(#{params}));
}

EOS
  end

  def get_decl(c)
    cname=c.name.caps
    gen "    #{cname}& #{@classname}::get#{cname}();\n"
  end
  
  def get_defn(c)
    cname=c.name.caps
    gen <<EOS
#{@classname}::#{c.name.caps}& #{@classname}::get#{c.name.caps}()
{
    return #{proxy_member(c)};
}
EOS
  end

  def generate
    # .h file
    h_file(@classname+".h") {
      gen <<EOS
#include "qpid/framing/Proxy.h"

namespace qpid {
namespace framing {

class #{@classname} : public Proxy
{
public:
    #{@classname}(ChannelAdapter& ch);

    // Inner class definitions
EOS
    @amqp.classes.each{ |c| inner_class_decl(c) }
    gen "    // Inner class instance get methods\n"
    @amqp.classes.each{ |c| get_decl(c) }
    gen <<EOS
  private:
    // Inner class instances
EOS
    indent { @amqp.classes.each{ |c| gen c.cppname+" "+proxy_member(c)+";\n" } }
    gen <<EOS
}; /* class #{@classname} */

} /* namespace framing */
} /* namespace qpid */
EOS
  }

  # .cpp file
  cpp_file(@classname+".cpp") {
    gen <<EOS
#include <sstream>
#include "#{@classname}.h"
#include "qpid/framing/ChannelAdapter.h"
#include "qpid/framing/amqp_types_full.h"
EOS
    @amqp.methods_on(@chassis).each { |m| include(m) }
    gen <<EOS
namespace qpid {
namespace framing {

#{@classname}::#{@classname}(ChannelAdapter& ch) :
EOS
    gen "    Proxy(ch)"
    @amqp.classes.each { |c| gen ",\n    "+proxy_member(c)+"(channel)" }
    gen <<EOS
    {}

    // Inner class instance get methods
EOS
    @amqp.classes.each { |c| get_defn(c) }
    gen "    // Inner class implementation\n\n"
    @amqp.classes.each { |c| inner_class_defn(c) }
    gen "}} // namespae qpid::framing"
  }
   end
end


ProxyGen.new("client", ARGV[0], Amqp).generate;
ProxyGen.new("server", ARGV[0], Amqp).generate;
    
