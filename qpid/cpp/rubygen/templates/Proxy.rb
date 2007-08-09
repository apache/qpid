#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class ProxyGen < CppGen
    
  def initialize(chassis, outdir, amqp)
    super(outdir, amqp)
    @chassis=chassis
    @classname="AMQP_#{@chassis.caps}Proxy"
    @filename="qpid/framing/#{@classname}"
  end

  def proxy_member(c) c.name.lcaps+"Proxy"; end
  
  def inner_class_decl(c)
    cname=c.name.caps
    gen <<EOS
    // ==================== class #{cname} ====================
    class #{cname}
    {
      private:
        ChannelAdapter& channel;
        RequestId responseTo;

      public:
        // Constructors and destructors

        #{cname}(ChannelAdapter& ch) : 
            channel(ch) {}
        virtual ~#{cname}() {}

        static #{cname}& get(#{@classname}& proxy) { return proxy.get#{cname}();}
        // set for response correlation
        void setResponseTo(RequestId r) { responseTo = r; }
                 
        // Protocol methods
EOS
    indent(2) { c.amqp_methods_on(@chassis).each { |m| inner_method_decl(m) } }
    gen "\n    }; // class #{cname}\n\n"
  end

  def inner_method_decl(m)
    genl "virtual RequestId #{m.cppname}(#{m.signature.join(",\n            ")});"
    genl
  end

  def inner_class_defn(c)
    cname=c.cppname
    gen "// ==================== class #{cname} ====================\n"
    c.amqp_methods_on(@chassis).each { |m| inner_method_defn(m, cname) }
  end
  
  def inner_method_defn(m,cname)
    genl "RequestId #{@classname}::#{cname}::#{m.cppname}(#{m.signature.join(", ")})"
    scope { 
      params=(["channel.getVersion()"]+m.param_names).join(", ")
      genl "return channel.send(make_shared_ptr(new #{m.body_name}(#{params})));"
    }
  end

  def get_decl(c)
    cname=c.name.caps
    gen "    #{cname}& get#{cname}();\n"
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
    h_file(@filename) {
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
    @amqp.amqp_classes.each{ |c| inner_class_decl(c) }
    gen "    // Inner class instance get methods\n"
    @amqp.amqp_classes.each{ |c| get_decl(c) }
    gen <<EOS
  private:
    // Inner class instances
EOS
    indent { @amqp.amqp_classes.each{ |c| gen c.cppname+" "+proxy_member(c)+";\n" } }
    gen <<EOS
}; /* class #{@classname} */

} /* namespace framing */
} /* namespace qpid */
EOS
  }

  # .cpp file
  cpp_file(@filename) {
      include "<sstream>"
      include "#{@classname}.h"
      include "qpid/framing/ChannelAdapter.h"
      include "qpid/framing/amqp_types_full.h"
      @amqp.amqp_methods_on(@chassis).each {
        |m| include "qpid/framing/#{m.body_name}.h" }
    gen <<EOS
namespace qpid {
namespace framing {

#{@classname}::#{@classname}(ChannelAdapter& ch) :
EOS
    gen "    Proxy(ch)"
    @amqp.amqp_classes.each { |c| gen ",\n    "+proxy_member(c)+"(channel)" }
    gen <<EOS
    {}

    // Inner class instance get methods
EOS
    @amqp.amqp_classes.each { |c| get_defn(c) }
    gen "    // Inner class implementation\n\n"
    @amqp.amqp_classes.each { |c| inner_class_defn(c) }
    genl "}} // namespae qpid::framing"
  }
   end
end


ProxyGen.new("client", ARGV[0], Amqp).generate;
ProxyGen.new("server", ARGV[0], Amqp).generate;
    
