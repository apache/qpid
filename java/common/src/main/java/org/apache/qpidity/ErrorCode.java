package org.apache.qpidity;

public enum ErrorCode
{
    //Qpid specific - for the time being
    UNDEFINED(1,"undefined",true),
    MESSAGE_REJECTED(1,"message_rejected",true),

    //This might change in the spec, the error class is not applicable
    NO_ERROR(200,"reply-success",true),

    //From the spec
    CONTENT_TOO_LARGE(311,"content-too-large",false),
    NO_ROUTE(312,"no-route",false),
    NO_CONSUMERS(313,"content-consumers",false),
    CONNECTION_FORCED(320,"connection-forced",true),
    INVALID_PATH(402,"invalid-path",true),
    ACCESS_REFUSED(403,"access-refused",false),
    NOT_FOUND(404,"not-found",false),
    RESOURCE_LOCKED(405,"resource-locked",false),
    PRE_CONDITION_FAILED(406,"precondition-failed",false),

    FRAME_ERROR(501,"frame_error",true),
    SYNTAX_ERROR(502,"syntax_error",true),
    COMMAND_INVALID(503,"command_invalid",true),
    SESSION_ERROR(504,"sesion_error",true),
    NOT_ALLOWED(530,"not_allowed",true),
    NOT_IMPLEMENTED(540,"not_implemented",true),
    INTERNAL_ERROR(541,"internal_error",true),
    INVALID_ARGUMENT(542,"invalid_argument",true);

    private int _code;
    private String _desc;
    private boolean _hardError;

    private ErrorCode(int code,String desc,boolean hardError)
    {
        _code = code;
        _desc= desc;
        _hardError = hardError;
    }

    public int getCode()
    {
        return _code;
    }

    public String getDesc()
    {
        return _desc;
    }

    private boolean isHardError()
    {
        return _hardError;
    }

    public static ErrorCode get(int code)
    {
        switch(code)
        {
            case 200 : return NO_ERROR;
            case 311 : return CONTENT_TOO_LARGE;
            case 312 : return NO_ROUTE;
            case 313 : return NO_CONSUMERS;
            case 320 : return CONNECTION_FORCED;
            case 402 : return INVALID_PATH;
            case 403 : return ACCESS_REFUSED;
            case 404 : return NOT_FOUND;
            case 405 : return RESOURCE_LOCKED;
            case 406 : return PRE_CONDITION_FAILED;
            case 501 : return FRAME_ERROR;
            case 502 : return SYNTAX_ERROR;
            case 503 : return COMMAND_INVALID;
            case 504 : return SESSION_ERROR;
            case 530 : return NOT_ALLOWED;
            case 540 : return NOT_IMPLEMENTED;
            case 541 : return INTERNAL_ERROR;
            case 542 : return INVALID_ARGUMENT;

            default : return UNDEFINED;
        }
    }
 }

/*

<constant name="internal-error" value="541" class="hard-error">
<doc>
  The server could not complete the method because of an internal error. The server may require
  intervention by an operator in order to resume normal operations.
</doc>
</constant>

<constant name="invalid-argument" value="542" class="hard-error">
<doc>
  An invalid or illegal argument was passed to a method, and the operation could not proceed.
</doc>
</constant>
*/