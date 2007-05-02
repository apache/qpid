package org.apache.qpid.server.txn;

/**
 * Created by Arnaud Simon
 * Date: 29-Mar-2007
 * Time: 14:57:01
 */
public enum XAFlag
{
    rbrollback(1, "XA-RBROLLBACK", "The rollback was caused by an unspecified reason"),
    rbtimeout(2, "XA-RBTIMEOUT", "The transaction branch took too long"),
    heurhaz(3, "XA-HEURHAZ", "The transaction branch may have been heuristically completed"),
    heurcom(4, "XA-HEURCOM", "The transaction branch has been heuristically committed"),
    heurrb(5, "XA-HEURRB", "The transaction branch has been heuristically rolled back"),
    heurmix(6, "XA-HEURMIX", "The transaction branch has been heuristically committed and rolled back"),
    rdonly(7, "XA-RDONLY", "The transaction branch was read-only and has been committed"),
    ok(8, "XA-OK", "Normal execution");

    private final int _code;

    private final String _name;

    private final String _description;

    XAFlag(int code, String name, String description)
    {
        _code = code;
        _name = name;
        _description = description;
    }

    //==============================================
    // Getter methods
    //==============================================

    public int getCode()
    {
        return _code;
    }

    public String getName()
    {
        return _name;
    }

    public String getDescription()
    {
        return _description;
    }
}
