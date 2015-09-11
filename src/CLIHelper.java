import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

/**
 * Created by Callum on 13/08/2015.
 */
public class CLIHelper
{
    public static void exec(String command)
    {
        try
        {
            Runtime.getRuntime().exec(command).waitFor(10, TimeUnit.SECONDS);
        } catch(IOException e)
        {
            e.printStackTrace();
        } catch(InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
