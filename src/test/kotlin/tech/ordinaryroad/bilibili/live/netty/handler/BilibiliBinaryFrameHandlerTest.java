/*
 * MIT License
 *
 * Copyright (c) 2023 OrdinaryRoad
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package tech.ordinaryroad.bilibili.live.netty.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import tech.ordinaryroad.bilibili.live.constant.CmdEnum;
import tech.ordinaryroad.bilibili.live.constant.ProtoverEnum;
import tech.ordinaryroad.bilibili.live.listener.IBilibiliSendSmsReplyMsgListener;
import tech.ordinaryroad.bilibili.live.msg.SendSmsReplyMsg;
import tech.ordinaryroad.bilibili.live.netty.frame.factory.BilibiliWebSocketFrameFactory;

import java.net.URI;

/**
 * @author mjz
 * @date 2023/1/7
 */
@Slf4j
class BilibiliBinaryFrameHandlerTest {

    @Test
    public void example() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            URI websocketURI = new URI("wss://broadcastlv.chat.bilibili.com:2245/sub");

            BilibiliHandshakerHandler bilibiliHandshakerHandler = new BilibiliHandshakerHandler(WebSocketClientHandshakerFactory.newHandshaker(
                    websocketURI, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()));
            BilibiliBinaryFrameHandler bilibiliHandler = new BilibiliBinaryFrameHandler(new IBilibiliSendSmsReplyMsgListener() {
                @Override
                public void onDanmuMsg(SendSmsReplyMsg msg) {
                    JsonNode info = msg.getInfo();
                    JsonNode jsonNode1 = info.get(1);
                    String danmuText = jsonNode1.asText();
                    JsonNode jsonNode2 = info.get(2);
                    Integer uid = jsonNode2.get(0).asInt();
                    String uname = jsonNode2.get(1).asText();
                    log.debug("???????????? {}({})???{}", uname, uid, danmuText);
                }

                @Override
                public void onSendGift(SendSmsReplyMsg msg) {
                    JsonNode data = msg.getData();
                    String action = data.get("action").asText();
                    String giftName = data.get("giftName").asText();
                    Integer num = data.get("num").asInt();
                    String uname = data.get("uname").asText();
                    Integer price = data.get("price").asInt();
                    log.debug("???????????? {} {} {}x{}({})", uname, action, giftName, num, price);
                }

                @Override
                public void onEnterRoom(SendSmsReplyMsg msg) {
                    log.debug("??????????????????????????? {}", msg.getData().get("uname").asText());
                }

                @Override
                public void onEntryEffect(SendSmsReplyMsg msg) {
                    JsonNode data = msg.getData();
                    String copyWriting = data.get("copy_writing").asText();
                    log.info("???????????? {}", copyWriting);
                }

                @Override
                public void onWatchedChange(SendSmsReplyMsg msg) {
                    JsonNode data = msg.getData();
                    int num = data.get("num").asInt();
                    String textSmall = data.get("text_small").asText();
                    String textLarge = data.get("text_large").asText();
                    log.debug("?????????????????? {} {} {}", num, textSmall, textLarge);
                }

                @Override
                public void onClickLike(SendSmsReplyMsg msg) {
                    JsonNode data = msg.getData();
                    String uname = data.get("uname").asText();
                    String likeText = data.get("like_text").asText();
                    log.debug("??????????????? {} {}", uname, likeText);
                }

                @Override
                public void onClickUpdate(SendSmsReplyMsg msg) {
                    JsonNode data = msg.getData();
                    int clickCount = data.get("click_count").asInt();
                    log.debug("??????????????? {}", clickCount);
                }

                @Override
                public void onOtherSendSmsReplyMsg(CmdEnum cmd, SendSmsReplyMsg msg) {
                    log.info("????????????\n{}", cmd);
                }
            });

            //????????????
            log.info("????????????");
            System.out.println(websocketURI.getScheme());
            System.out.println(websocketURI.getHost());
            System.out.println(websocketURI.getPort());
//            SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            SslContext sslCtx = SslContextBuilder.forClient().build();

            Bootstrap bootstrap = new Bootstrap()
                    .group(workerGroup)
                    // ??????Channel
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    // Channel??????
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // ?????????
                            ChannelPipeline pipeline = ch.pipeline();

                            //??????????????? addFirst ??????wss???????????????
                            pipeline.addFirst(sslCtx.newHandler(ch.alloc(), websocketURI.getHost(), websocketURI.getPort()));

                            // ????????????http???????????????
                            pipeline.addLast(new HttpClientCodec());
                            // ?????????????????????????????????????????????
                            pipeline.addLast(new ChunkedWriteHandler());
                            // ???????????????????????????????????????????????????HttpMessage?????????FullHttpRequest/Response
                            pipeline.addLast(new HttpObjectAggregator(1024 * 64));

//                            pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
//                            pipeline.addLast(new WebSocketServerProtocolHandler("/sub", null, true, 65536 * 10));

                            // ???????????????
                            pipeline.addLast(bilibiliHandshakerHandler);
                            pipeline.addLast(bilibiliHandler);
                        }
                    });

            final Channel channel = bootstrap.connect(websocketURI.getHost(), websocketURI.getPort()).sync().channel();

            // ??????????????????????????????
            bilibiliHandshakerHandler.handshakeFuture().sync();

            // 5s?????????
            log.info("???????????????");
            channel.writeAndFlush(
                    BilibiliWebSocketFrameFactory.getInstance(ProtoverEnum.NORMAL_ZLIB)
//                             7777
//                            .createAuth(545068)
                            .createAuth(21509476)
//                            .createAuth(7396329)
            );

            channel.closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}