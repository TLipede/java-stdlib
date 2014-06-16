/**
 * 
 */
package com.krux.stdlib;

import static java.util.Arrays.asList;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krux.server.http.StdHttpServer;
import com.krux.stdlib.logging.LoggerConfigurator;
import com.krux.stdlib.statsd.HeapStatsdReporter;
import com.krux.stdlib.statsd.KruxStatsdClient;
import com.krux.stdlib.statsd.NoopStatsdClient;
import com.krux.stdlib.statsd.StatsdClient;

/**
 * @author casspc
 * 
 */
public class KruxStdLib {

    final static Logger logger = (Logger) LoggerFactory.getLogger(KruxStdLib.class);

    /**
     * See
     */
    public static StatsdClient statsd = null;
    public static String env;
    public static String appName;
    public static String baseAppDir;
    public static String statsdEnv;

    private static OptionParser _parser = null;
    private static OptionSet _options = null;
    private static boolean _initialized = false;
    
    public static boolean httpListenerRunning = false;
    
    private static int httpPort = 0;

    // holds all registered Runnable shutdown hooks (which are executed
    // synchronously in the
    // order added to this list.
    private static List<Runnable> shutdownHooks = Collections.synchronizedList(new ArrayList<Runnable>());

    // holds list of registered ChannelInboundHandlerAdapters for serving http
    // responses
    private static Map<String, ChannelInboundHandlerAdapter> httpHandlers = new HashMap<String, ChannelInboundHandlerAdapter>();
    
    private static String _appDescription = null;

    /**
     * If you'd like to utilize the std lib parser for your app-specific cli
     * argument parsing needs, feel free to pass a configured OptionParser to
     * this class before initializing it. (see this class' source for examples
     * and http://pholser.github.io/jopt-simple/examples.html for details on how
     * to create and configure the OptionParser)
     * 
     * @param parser
     *            a Configured OptionParser
     */
    public static void setOptionParser(OptionParser parser) {
        _parser = parser;
    }
    
    /**
     * Initializes the std libary static functions, including logging, statsd
     * client and a "status" http listener
     * 
     * @param args
     *            The command line args that were passed to your main( String[]
     *            args )
     * @return the OptionSet of parsed command line arguments
     */
    public static OptionSet initialize(String appDescription, String[] args) {
        _appDescription = appDescription;
        return initialize( args );
    }

    /**
     * Initializes the std libary static functions, including logging, statsd
     * client and a "status" http listener
     * 
     * @param args
     *            The command line args that were passed to your main( String[]
     *            args )
     * @return the OptionSet of parsed command line arguments
     */
    public static OptionSet initialize(String[] args) {

        if (!_initialized) {
            // parse command line, handle common needs

            // some defaults
            final String defaultStatsdHost = "localhost";
            final int defaultStatsdPort = 8125;
            final String defaultEnv = "dev";
            final String defaultLogLevel = "DEBUG";
            final String defaultAppName = getMainClassName();
            final Integer httpListenerPort = 0;
            final String baseAppDirDefault = "/tmp";
            final String statsEnvironmentDefault = "dev";
            final int defaultHeapReporterIntervalMs = 1000;

            OptionParser parser;
            if (_parser == null) {
                parser = new OptionParser();
            } else {
                parser = _parser;
            }

            List<String> synonyms = asList( "help", "h" );
            parser.acceptsAll(synonyms, "Prints this helpful message");
            OptionSpec enableStatsd = parser.accepts("stats", "Enable/disable statsd broadcast");
            OptionSpec<String> statsdHost = parser.accepts("stats-host", "Listening statsd host").withOptionalArg()
                    .ofType(String.class).defaultsTo(defaultStatsdHost);
            OptionSpec<Integer> statsdPort = parser.accepts("stats-port", "Listening statsd port").withOptionalArg()
                    .ofType(Integer.class).defaultsTo(defaultStatsdPort);
            OptionSpec<String> environment = parser.accepts("env", "Operating environment").withOptionalArg()
                    .ofType(String.class).defaultsTo(defaultEnv);
            OptionSpec<String> logLevel = parser.accepts("log-level", "Default log4j log level").withOptionalArg()
                    .ofType(String.class).defaultsTo(defaultLogLevel);
            OptionSpec<String> appNameOption = parser
                    .accepts(
                            "app-name",
                            "Application identifier, used for statsd namespaces, log file names, etc. "
                                    + "If not supplied, will use this app's entry point classname.").withOptionalArg()
                    .ofType(String.class).defaultsTo(defaultAppName);
            OptionSpec<Integer> httpListenPort = parser.accepts("http-port", "Accept http connections on this port (0 = web server will not start)")
                    .withOptionalArg().ofType(Integer.class).defaultsTo(httpListenerPort);
            OptionSpec<String> baseAppDirectory = parser.accepts("base-dir", "Base directory for app needs.").withOptionalArg()
                    .ofType(String.class).defaultsTo(baseAppDirDefault);
            OptionSpec<String> statsEnvironment = parser.accepts("stats-environment", "Stats environment (dictates statsd prefix)").withOptionalArg()
                    .ofType(String.class).defaultsTo(statsEnvironmentDefault);
            OptionSpec<Integer> heapReporterIntervalMs = parser.accepts("heap-stats-interval-ms", "Interval (ms) for used heap statsd gauge")
                    .withOptionalArg().ofType(Integer.class).defaultsTo(defaultHeapReporterIntervalMs);


            _options = parser.parse(args);
            
            //set base app dir
            baseAppDir = _options.valueOf(baseAppDirectory);
            statsdEnv = _options.valueOf(statsEnvironment);
            httpPort = _options.valueOf(httpListenPort);

            // if "--help" was passed in, show some helpful guidelines and exit
            if (_options.has("help")) {
                try {
                    if ( _appDescription != null )
                        System.out.println( _appDescription );
                    parser.formatHelpWith( new KruxHelpFormatter() );
                    parser.printHelpOn(System.out);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } finally {
                    System.exit(0);
                }
            }

            // set environment
            env = _options.valueOf(environment);

            // set global app name
            appName = _options.valueOf(appNameOption);

            // setup logging level
            LoggerConfigurator.configureLogging(baseAppDir + "/logs", _options.valueOf(logLevel), appName);

            // setup statsd            
            try {
                //this one is not like the others.  passing "--stats", with or without a value, enables statsd
                if (_options.has(enableStatsd)) {
                    logger.info( "statsd metrics enabled" );
                    statsd = new KruxStatsdClient(_options.valueOf(statsdHost), _options.valueOf(statsdPort), logger);
                } else {
                    statsd = new NoopStatsdClient(InetAddress.getLocalHost(), 0);
                }
            } catch (Exception e) {
                logger.warn("Cannot establish a statsd connection", e);
            }


            // finally, setup a shutdown thread to run all registered
            // application hooks
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    for (Runnable r : shutdownHooks) {
                        r.run();
                    }
                }
            });
            
            //setup a simple maintenance timer for reporting used heap size
            // and other stuff in the future
            final int heapStatsInterval = _options.valueOf(heapReporterIntervalMs);
            final TimerTask timerTask = new HeapStatsdReporter();
            final Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(timerTask, 2 * 1000, heapStatsInterval);    
            
            //make sure we cancel that time, jic
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        timer.cancel();
                    } catch ( Exception e ) {
                        logger.warn( "Error while attemptin to shut down heap reporter", e );
                    }
                }
            });
            
            //set up an http listener if the submitted port != 0
            // start http service on a separate thread
            if (httpPort != 0) {
                Thread t = new Thread(new StdHttpServer(httpPort, httpHandlers));
                t.setName("MainHttpServerThread");
                t.start();
                httpListenerRunning = true;
            } else {
                logger.warn("Not starting HTTP listener, cli option 'http-port' is not set");
            }

            _initialized = true;
            logger.info( "** Started " + appName + " **" );
            for ( OptionSpec<?> spec : _options.specs() ) {
                logger.info( spec.toString() + " : " + _options.valuesOf( spec ) );
            }
        }
        return _options;
    }

    private static String getMainClassName() {

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StackTraceElement main = stack[stack.length - 1];
        String mainClass = main.getClassName();
        if (mainClass.contains(".")) {
            String[] parts = mainClass.split("\\.");
            mainClass = parts[parts.length - 1];
        }
        return mainClass;
    }

    public static void registerShutdownHook(Runnable r) {
        shutdownHooks.add(r);
    }

    public static void registerHttpHandler(String url, ChannelInboundHandlerAdapter handler) {
        if (!_initialized) {
            if (!url.contains("__status")) {
                httpHandlers.put(url, handler);
            }
        }
    }

}
