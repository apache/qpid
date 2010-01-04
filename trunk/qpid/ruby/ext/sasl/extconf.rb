require 'mkmf'

extension_name = 'sasl'
have_library("c", "main")

unless have_library("sasl2")
    raise "Package cyrus-sasl-devel not found"
end

create_makefile(extension_name)
