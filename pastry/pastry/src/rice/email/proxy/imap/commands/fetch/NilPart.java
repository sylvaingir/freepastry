package rice.email.proxy.imap.commands.fetch;

import rice.email.proxy.mail.StoredMessage;


public class NilPart
    extends FetchPart
{
    public boolean canHandle(Object req)
    {

        return false;
    }

    public String fetch(StoredMessage msg, Object part)
    {
        return part + " NIL";
    }
}