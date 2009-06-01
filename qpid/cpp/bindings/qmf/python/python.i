%module qmfengine

// These are probably wrong.. just to get it to compile for now.
%typemap (in) void *
{
    $1 = (void *) $input;
}

%typemap (out) void *
{
    $result = (PyObject *) $1;
}


%include "../qmfengine.i"

