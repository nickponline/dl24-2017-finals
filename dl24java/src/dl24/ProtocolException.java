package dl24;

public class ProtocolException
    extends RuntimeException
{
    public ProtocolException(String message)
    {
        super(message);
    }
    
    public ProtocolException(String message, Exception exception)
    {
        super(message, exception);
    }
}
