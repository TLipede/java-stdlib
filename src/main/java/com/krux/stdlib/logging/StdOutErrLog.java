package com.krux.stdlib.logging;

import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for getting all stdout and stderr into sl4j logs
 * 
 * put StdOutErrLog.tieSystemOutAndErrToLog() somewhere in your main(String[]
 * args) to use
 * 
 * @author casspc
 * 
 */
public class StdOutErrLog {

    private static final Logger logger = LoggerFactory.getLogger( StdOutErrLog.class );

    public static void tieSystemOutAndErrToLog() {
        System.setOut( createLoggingProxy( System.out ) );
        System.setErr( createLoggingProxyErr( System.err ) );
    }

    public static PrintStream createLoggingProxy( final PrintStream realPrintStream ) {
        return new PrintStream( realPrintStream ) {
            public void print( final String string ) {
                realPrintStream.print( string );
                logger.info( string );
            }
        };
    }

    public static PrintStream createLoggingProxyErr( final PrintStream realPrintStream ) {
        return new PrintStream( realPrintStream ) {
            public void print( final String string ) {
                realPrintStream.print( string );
                logger.error( string );
            }
        };
    }
}