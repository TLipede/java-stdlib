package com.krux.server.http;

import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krux.stdlib.KruxStdLib;

public class StdHttpServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(StdHttpServerHandler.class.getName());
    
    private final static String STATUS_URL = "__status";
    
    private static HttpResponseStatus statusCode = HttpResponseStatus.OK;
    private static String statusResponseMessage = KruxStdLib.APP_NAME + " is running nominally";

    private static final String BODY_404 = "<html><head><title>404 Not Found</title></head> <body bgcolor=\"white\"> <center><h1>404 Not Found</h1></center> <hr><center>Krux - " + KruxStdLib.APP_NAME + "</center> </body> </html>";
    
    private Map<String, ChannelInboundHandlerAdapter> _httpHandlers;

    public StdHttpServerHandler(Map<String, ChannelInboundHandlerAdapter> httpHandlers) {
        _httpHandlers = httpHandlers;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        //ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            long start = System.currentTimeMillis();

            HttpRequest req = (HttpRequest) msg;
            String uri = req.getUri();
            
            String[] parts = uri.split("\\?");
            String path = parts[0];
            log.info("path: " + path);
            
            if (is100ContinueExpected(req)) {
                ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
            }
            boolean keepAlive = isKeepAlive(req);
            
            if ( path.trim().endsWith( STATUS_URL ) ) {
                FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer( 
                        ("{'status':'" + statusCode.reasonPhrase() + "','version':" + KruxStdLib.APP_VERSION + ",'state':'" + statusResponseMessage + "'}").getBytes() ));
                res.headers().set(CONTENT_TYPE, "application/json");
                res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
                if (!keepAlive) {
                    ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
                } else {
                    res.headers().set(CONNECTION, Values.KEEP_ALIVE);
                    ctx.writeAndFlush(res);
                }
                
            } else {
                
                ChannelInboundHandlerAdapter handler = _httpHandlers.get( path );
                if ( handler != null ) {
                    //pass control to submitted handler
                	log.info( "Found handler" );
                    ChannelPipeline p = ctx.pipeline();
                    p.addLast( "final_handler", handler.getClass().newInstance() );
                    
                    //is this really the best way?
                    ctx.fireChannelRead(msg);
                    ctx.fireChannelReadComplete();
                    
                } else {

                	log.info( "No configured URL, returning 404" );
                    FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, Unpooled.wrappedBuffer( BODY_404.getBytes( ) ) );
                    res.headers().set(CONTENT_TYPE, "text/html");
                    res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
                    if (!keepAlive) {
                        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
                    } else {
                        res.headers().set(CONNECTION, Values.KEEP_ALIVE);
                        ctx.writeAndFlush(res);
                    }
                    
                    KruxStdLib.STATSD.count( KruxStdLib.APP_NAME + "_HTTP_404" );
                }
            }
            
            ReferenceCountUtil.release(msg);
            long time = System.currentTimeMillis() - start;
            log.info("Request took " + time + "ms for whole request");
            KruxStdLib.STATSD.time( KruxStdLib.APP_NAME + "_HTTP_200", time);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Error while processing request", cause);
        KruxStdLib.STATSD.count( KruxStdLib.APP_NAME + "_HTTP_503" );
        ctx.close();
    }
    
    public static void setStatusCodeAndMessage( HttpResponseStatus code, String message ) {
        statusCode = code;
        statusResponseMessage = message;
    }
    
    public static void resetStatusCodeAndMessageOK() {
        statusCode = HttpResponseStatus.OK;
        statusResponseMessage = KruxStdLib.APP_NAME + " is running nominally";
    }
}