package com.krux.stdlib.utils;

import com.krux.stdlib.KruxStdLib;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of utility methods to interact with the Krux Console RDS instance.
 *
 * @author Vivek S. Vaidya
 */
public class DbUtils {

    /**
     * Get a database connection for the specified environment.
     *
     * @return the database connection if one could be established; null otherwise
     */
    private static final Logger log = LoggerFactory.getLogger(DbUtils.class.getName());

    public static Connection getDbConnection() {
        ConfigProperties kprops = new ConfigProperties();
        Connection conn = null;
        long start = System.currentTimeMillis();
        try {
            Class.forName(kprops.getJdbcDriver());
            conn = DriverManager.getConnection(kprops.getJdbcUrl(), kprops.getJdbcUser(),
                    kprops.getJdbcPassword());
        } catch (Exception ex) {
            String errorMsg = ex.getMessage();
            log.error("db_util", errorMsg);
            conn = null;
        }
        long time = System.currentTimeMillis() - start;
        log.info("DB get connection took " + time + "ms.");
        KruxStdLib.STATSD.time(KruxStdLib.APP_NAME + "_get_db_conn", time);
        return conn;
    }

    /**
     * Close the specified database connection.
     *
     * @param conn the database connection to close.
     */
    public static void close(Connection conn) {
        long start = System.currentTimeMillis();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException sqe) {
                // an error occured while closing the connection.
                // there's really not much we can do at this point, so we log the exception and return
                String errorMsg = sqe.getMessage();
                log.error("db_util", errorMsg);
            }
        }
        long time = System.currentTimeMillis() - start;
        log.info("DB connection close took " + time + "ms.");
        KruxStdLib.STATSD.time(KruxStdLib.APP_NAME + "_close_db_conn", time);
    }

    /**
     * Close the specified (Prepared) Statement
     *
     * @param ps the statement to close
     */
    public static void close(Statement ps) {
        long start = System.currentTimeMillis();
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException sqe) {
                // an error occured while closing the statement
                // there's really not much we can do at this point, so we log the exception and return
                String errorMsg = sqe.getMessage();
                log.error("db_util", errorMsg);
            }
        }
        long time = System.currentTimeMillis() - start;
        log.info("DB PreparedStatement close took " + time + "ms.");
        KruxStdLib.STATSD.time(KruxStdLib.APP_NAME + "_close_db_ps", time);
    }

    /**
     * Close the specified result set.
     *
     * @param rs the result set to close
     */
    public static void close(ResultSet rs) {
        long start = System.currentTimeMillis();
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException sqe) {
                // an error occured while closing the statement
                // there's really not much we can do at this point, so we log the exception and return
                String errorMsg = sqe.getMessage();
                log.error("db_util", errorMsg);
            }
        }
        long time = System.currentTimeMillis() - start;
        log.info("DB ResultSet close took " + time + "ms.");
        KruxStdLib.STATSD.time(KruxStdLib.APP_NAME + "_close_db_rs", time);
    }
}
