package dl24.test;
import dl24.Client;

public class Test
{
    public static void main(String[] args)
        throws Exception
    {
        Client client = null;
        try {
            client = new Client("foo", "bar", "localhost", 12345, 8081);
            client.writeCommand("TEST", 1, 3.4, "hello", 'c');
            while (true) {
                String line = client.readLine();
                System.err.println("Received: " + line);
            }
        }
        catch (Exception e) {
            if (client != null) {
                client.stop();
            }
            throw e;
        }
    }
}
