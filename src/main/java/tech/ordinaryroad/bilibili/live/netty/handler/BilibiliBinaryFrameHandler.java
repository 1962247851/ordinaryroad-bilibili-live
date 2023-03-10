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

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import tech.ordinaryroad.bilibili.live.constant.CmdEnum;
import tech.ordinaryroad.bilibili.live.constant.ProtoverEnum;
import tech.ordinaryroad.bilibili.live.listener.IBilibiliSendSmsReplyMsgListener;
import tech.ordinaryroad.bilibili.live.msg.SendSmsReplyMsg;
import tech.ordinaryroad.bilibili.live.msg.base.BaseBilibiliMsg;
import tech.ordinaryroad.bilibili.live.netty.frame.factory.BilibiliWebSocketFrameFactory;
import tech.ordinaryroad.bilibili.live.util.BilibiliCodecUtil;

import java.util.concurrent.TimeUnit;


/**
 * @author mjz
 * @date 2023/1/4
 */
@Slf4j
public class BilibiliBinaryFrameHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    /**
     * ????????????????????????
     */
    private ScheduledFuture<?> scheduledFuture = null;
    private final IBilibiliSendSmsReplyMsgListener listener;

    public BilibiliBinaryFrameHandler(IBilibiliSendSmsReplyMsgListener listener) {
        this.listener = listener;
    }

    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame message) throws Exception {
        ByteBuf byteBuf = message.content();
        BaseBilibiliMsg msg = BilibiliCodecUtil.decode(byteBuf);
        if (msg == null) {
            return;
        }

        if (msg instanceof SendSmsReplyMsg sendSmsReplyMsg) {
            CmdEnum cmd = sendSmsReplyMsg.getCmd();
            // log.debug("?????? {} ?????? {}", cmd, msg);
            switch (cmd) {
                case DANMU_MSG -> listener.onDanmuMsg(sendSmsReplyMsg);
                case SEND_GIFT -> listener.onSendGift(sendSmsReplyMsg);
                case INTERACT_WORD -> listener.onEnterRoom(sendSmsReplyMsg);
                case ENTRY_EFFECT -> listener.onEntryEffect(sendSmsReplyMsg);
                case WATCHED_CHANGE -> listener.onWatchedChange(sendSmsReplyMsg);
                case LIKE_INFO_V3_CLICK -> listener.onClickLike(sendSmsReplyMsg);
                case LIKE_INFO_V3_UPDATE -> listener.onClickUpdate(sendSmsReplyMsg);
                case HOT_RANK_CHANGED_V2 -> {
                    // TODO ??????????????????????????????
                }
                case ONLINE_RANK_COUNT -> {
                    // TODO ?????????????????????
                }
                case ROOM_REAL_TIME_MESSAGE_UPDATE -> {
                    // TODO ????????????????????????
                }
                case STOP_LIVE_ROOM_LIST -> {
                    // TODO ????????????????????????
                }
                case ONLINE_RANK_V2 -> {
                    // TODO ????????????????????? ??????
                }
                default -> {
                    listener.onOtherSendSmsReplyMsg(cmd, sendSmsReplyMsg);
                }
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.error("userEventTriggered {}", evt.getClass());
        if (evt instanceof ChannelInputShutdownReadComplete) {
            // TODO
        } else if (evt instanceof SslHandshakeCompletionEvent) {
            if (null != scheduledFuture && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
            scheduledFuture = ctx.executor().scheduleAtFixedRate(() -> {
                ctx.writeAndFlush(
                        BilibiliWebSocketFrameFactory.getInstance(ProtoverEnum.NORMAL_ZLIB)
                                .createHeartbeat()
                );
                log.info("???????????????");
            }, 15, 30, TimeUnit.SECONDS);
        } else if (evt instanceof SslCloseCompletionEvent) {
            if (null != scheduledFuture && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }
        } else {
            log.error("????????? {}", evt.getClass());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause.getCause() instanceof UnrecognizedPropertyException) {
            log.error("???????????????{}", cause.getMessage());
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
