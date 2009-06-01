%include stl.i
%trackobjects;

%module qmfengine

%typemap (in) void *
{
    $1 = (void *) $input;
}

%typemap (out) void *
{
    $result = (VALUE) $1;
}

%typemap (in) uint32_t
{
    $1 = FIX2UINT ((uint32_t) $input);
}

%typemap (out) uint32_t
{
    $result = UINT2NUM((unsigned int) $1);
}

%typemap (typecheck, precedence=SWIG_TYPECHECK_INTEGER) uint32_t {
   $1 = FIXNUM_P($input);
}

%typemap (in) uint64_t
{
    $1 = FIX2ULONG ((uint64_t) $input);
}

%typemap (out) uint64_t
{
    $result = ULONG2NUM((unsigned long) $1);
}

%typemap (typecheck, precedence=SWIG_TYPECHECK_INTEGER) uint64_t {
   $1 = FIXNUM_P($input);
}


%include "../qmfengine.i"

