#
# Generic AMQP code generation library.
#

require 'delegate'
require 'rexml/document'
require 'pathname'
include REXML

# Handy String functions for converting names.
class String
  # Convert to CapitalizedForm.
  def caps() gsub( /(^|\W)(\w)/ ) { |m| $2.upcase } end

  # Convert to underbar_separated_form.
  def bars() tr('- .','_'); end

  # Convert to lowerCaseCapitalizedForm
  def lcaps() gsub( /\W(\w)/ ) { |m| $1.upcase } end
end

# Sort an array by name.
class Array
  def sort_by_name()
    sort() { |a,b| a.name <=> b.name }
  end
end

# Add collect to Elements
class Elements
  def collect(xpath, &block)
    result=[]
    each(xpath) { |el| result << yield(el) }
    result
  end
end

# An AmqpElement extends (delegates to) a REXML::Element
# 
# NB: AmqpElements cache various values, they assume that
# the XML model does not change after the AmqpElement has
# been created.
# 
class AmqpElement < DelegateClass(Element)
  def initialize(xml, amqp) super(xml); @amqp_parent=amqp; end

  attr_reader :amqp_parent

  # Return the name attribute, not the element name.
  def name() attributes["name"]; end

  def amqp_root()
    amqp_parent ? amqp_parent.amqp_root : self;
  end
end

# AMQP field element
class AmqpField < AmqpElement
  def initialize(xml, amqp) super; end;

  # Get AMQP type for a domain name.
  def domain_type(name)
    domain=elements["/amqp/domain[@name='#{name}']"]
    (domain and domain.attributes["type"] or name)
  end

  # Get the AMQP type of this field.
  def field_type()
    d=attributes["domain"]
    dt=domain_type d if d
    (dt or attributes["type"])
  end
end

# AMQP method element
class AmqpMethod < AmqpElement
  def initialize(xml, amqp) super; end

  def content()
    attributes["content"]
  end

  def index() attributes["index"];  end

  def fields()
    @cache_fields ||= elements.collect("field") { |f| AmqpField.new(f,self); }
  end

  # Responses to this method (0-9)
  def responses()
    @cache_responses ||= elements.collect("response") { |el| AmqpMethod.new(el,self) }
  end

  # Methods this method responds to (0-9)
  def responds_to()
    @cache_responds_to ||= elements.collect("../method/response[@name='#{attributes['name']}']") { |el|
      AmqpMethod.new(el.parent, amqp_parent)
    }
  end

  def request?() responds_to().empty?; end
  def response?() not request?; end
end

# AMQP class element.
class AmqpClass < AmqpElement
  def initialize(xml,amqp) super; end
  def index() attributes["index"];  end
  def methods()
    @cache_methods ||= elements.collect("method") { |el|
      AmqpMethod.new(el,self)
    }.sort_by_name
  end

  # chassis should be "client" or "server"
  def methods_on(chassis)
    elements.collect("method/chassis[@name='#{chassis}']/..") { |m|
      AmqpMethod.new(m,self)
    }.sort_by_name
  end
end

# AMQP root element.
class AmqpRoot < AmqpElement

  # Initialize with output directory and spec files from ARGV.
  def initialize(*specs)
    specs.size or raise "No XML spec files."
    specs.each { |f| File.exists?(f) or raise "Invalid XML file: #{f}"}
    super Document.new(File.new(specs.shift)).root, nil
    specs.each { |s|            # Merge in additional specs
      root=Document.new(File.new(s)).root
      merge(self,root)
    }
  end

  def version()
    attributes["major"]+"-"+attributes["minor"]
  end

  def classes()
    @cache_classes ||= elements.collect("class") { |c| AmqpClass.new(c,self) }.sort_by_name
  end
  
  # Return all methods on all classes.
  def methods() classes.collect { |c| c.methods }.flatten;  end
  
  # Return all methods on chassis for all classes.
  def methods_on(chassis)
    classes.collect { |c| c.methods_on(chassis) }.flatten
  end

  # Merge contents of elements.
  def merge(to,from)
    from.elements.each { |from_child|
      tag,name = from_child.name, from_child.attributes["name"]
      to_child=to.elements["./#{tag}[@name='#{name}']"]
      to_child ? merge(to_child, from_child) : to.add(from_child.clone) }
  end

  private :merge
  
end

# Base class for code generators.
# Supports setting a per-line prefix, useful for e.g. indenting code.
# 
class Generator
  # Takes directory for output or "-", meaning print file names that
  # would be generated.
  def initialize (outdir, amqp)
    @amqp=amqp
    @outdir=outdir
    @prefix=''                  # For indentation or comments.
    @indentstr='    '           # One indent level.
    raise "Invalid output directory: #{outdir}" unless @outdir=="-" or File.directory?(@outdir) 
  end

  # Create a new file, set @out. 
  def file(file)
    puts file                   
    if (@outdir != "-")         
      path=Pathname.new "#{@outdir}/#{file}"
      path.parent.mkpath
      path.open('w') { |@out| yield }
    end
  end

  # Append multi-line string to generated code, prefixing each line.
  def gen (str)
    str.each_line { |line|
      @out << @prefix unless @midline
      @out << line
      @midline = nil
    }
    # Note if we stopped mid-line
    @midline = /[^\n]\z/ === str
  end

  # Append str + '\n' to generated code.
  def genl(str="")
    gen str
    gen "\n"
  end

  # Generate code with added prefix.
  def prefix(add)
    save=@prefix
    @prefix+=add
    yield
    @prefix=save
  end
  
  # Generate indented code
  def indent(n=1,&block) prefix(@indentstr * n,&block); end

  attr_accessor :out
end



