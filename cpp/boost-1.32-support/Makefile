.PHONY: default apply

default:
	@echo "Run 'make apply' to patch the source"

apply: main.patch ../src/boost
	patch -d .. -p0 < main.patch

../src/boost: boost.tar.gz
	tar -C .. -xvzf boost.tar.gz
