package dl24;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.MetricsServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Client
{
    private final Socket mySocket;
    private final PrintWriter myWriter;
    private final BufferedReader myReader;

    private final Map<String,Summary> mySummaries;

    private final Histogram commandTime;

    private final Server myPrometheusServer;
    private final CollectorRegistry registry;
    
    private int commandsUsed = 0;
    
    private final String appName;
    
    public Client(String appName,
                  String username,
                  String password,
                  String host,
                  int port,
                  int prometheusPort)
        throws IOException, ProtocolException
    {
        this.appName = appName;
        mySocket = new Socket(host, port);
        mySocket.setTcpNoDelay(true);
        myWriter = new PrintWriter(mySocket.getOutputStream());
        myReader = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));

        if (readLine("LOGIN|PROXY-NOLOGIN").equals("LOGIN")) {
            if (username == null) {
                username = System.getenv("DL24_USER");
            }
            if (password == null) {
                password = System.getenv("DL24_PASS");
            }
            writeLine(username);
            readLine("PASS");
            writeLine(password);
            readOk();
        }

        if (prometheusPort > 0) {
            // Start an HTTP server to expose Prometheus metrics.
            registry = new CollectorRegistry();
            myPrometheusServer = new Server(prometheusPort);
            ServletHandler handler = new ServletHandler();
            myPrometheusServer.setHandler(handler);
            handler.addServletWithMapping(new ServletHolder(new MetricsServlet(registry)), "/metrics");
            try {
                myPrometheusServer.start();
            }
            catch (Exception e) {
                throw new IOException("Failed to start HTTP server", e);
            }
            
            // Create a store for our per-command metrics.
            mySummaries = new HashMap<>();
            commandTime = Histogram.build().labelNames("command").name("dl24_command_time_seconds").help("Time between issuing command and receiving reply").register(registry);
        }
        else {
            mySummaries = null;
            commandTime = null;
            myPrometheusServer = null;
            registry = null;
        }
    }
    
    public void readOk()
        throws IOException, ProtocolException
    {
        readLine("OK");
    }
    
    public String readLine()
        throws IOException, ProtocolException
    {
        return readLine(null);
    }
    
    public String readLine(String expectedRegex)
        throws IOException, ProtocolException
    {
        final String line = myReader.readLine();
        if (line == null) {
            // End of stream.
            throw new EOFException();
        }
        if (expectedRegex != null && !line.matches(expectedRegex)) {
            throw new ProtocolException("Expected line of the form '" + expectedRegex + "' but got '" + line + "'");
        }
        else {
            return line;
        }
    }
    
    public void writeCommand(String command, Object... args)
        throws IOException, ProtocolException
    {
        final Histogram.Timer timer;
        if (commandTime != null) {
            timer = commandTime.labels(command).startTimer();
        }
        else {
            timer = null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(command);
            for (Object arg : args) {
                sb.append(' ');
                sb.append(arg.toString());
            }
            writeLine(sb.toString());
            commandsUsed++;
            readOk();
        }
        finally {
            if (timer != null) {
                timer.observeDuration();
            }
        }
    }

    public void writeLine(String line)
    {
        myWriter.println(line);
        myWriter.flush();
    }
    
    public void stop()
        throws Exception
    {
        if (myPrometheusServer != null) {
            System.err.println("Stopping HTTP server");
            myPrometheusServer.stop();
        }
    }
    
    public void doWait()
        throws IOException, ProtocolException
    {
        writeCommand("WAIT");
        
        // Now consume one line of the form "WAITING <time>"
        String[] parts = readLine("WAITING [0-9.]+").split(" ");
        System.err.println("Waiting " + Double.parseDouble(parts[1]) + " for next turn");
        readOk();
        commandsUsed = 0;
    }
    
    public void logPrometheusSummary(String name, double value)
    {
        if (mySummaries != null) {
            final Summary summary = mySummaries.computeIfAbsent(
                "user_" + name,
                c -> Summary.build()
                            .name("user_" + name)
                            .help("user var " + name).register(registry)
            );
            summary.observe(value);
        }
    }
    
    public int getCommandsUsed()
    {
        return commandsUsed;
    }
}
