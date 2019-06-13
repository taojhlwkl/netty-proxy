package org.hum.nettyproxy.adapter.socks5.handler;

import org.hum.nettyproxy.common.Constant;
import org.hum.nettyproxy.common.codec.DynamicLengthDecoder;
import org.hum.nettyproxy.common.codec.NettyProxyBuildSuccessMessageCodec.NettyProxyBuildSuccessMessage;
import org.hum.nettyproxy.common.codec.NettyProxyConnectMessageCodec;
import org.hum.nettyproxy.common.handler.DecryptPipeChannelHandler;
import org.hum.nettyproxy.common.handler.EncryptPipeChannelHandler;
import org.hum.nettyproxy.common.handler.ForwardHandler;
import org.hum.nettyproxy.common.handler.InactiveHandler;
import org.hum.nettyproxy.common.util.NettyBootstrapUtil;
import org.hum.nettyproxy.core.ConfigContext;
import org.hum.nettyproxy.core.NettyProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdStatus;

public class SocksInsideServerHandler extends SimpleChannelInboundHandler<SocksCmdRequest> {

	private static final Logger logger = LoggerFactory.getLogger(SocksInsideServerHandler.class);

	@Override
	protected void channelRead0(final ChannelHandlerContext browserCtx, final SocksCmdRequest msg) throws Exception {

		if (msg.host() == null || msg.host().isEmpty()) {
			browserCtx.close();
			return;
		}
		
		NettyProxyConfig config = ConfigContext.getConfig();

		if (msg.port() == 443) {
			browserCtx.pipeline().remove(this);
		}
		
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(browserCtx.channel().eventLoop());
		bootstrap.channel(NioSocketChannel.class);
		NettyBootstrapUtil.initTcpServerOptions(bootstrap, config);
		bootstrap.handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ch.pipeline().addLast(new PrepareConnectChannelHandler(browserCtx, msg));
			}
		});
		bootstrap.connect(config.getOutsideProxyHost(), config.getOutsideProxyPort()).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(final ChannelFuture outsideServerChannelFuture) throws Exception {
				logger.info("connect {}:{} successfully.", config.getOutsideProxyHost(), config.getOutsideProxyPort());

				byte[] hostBytes = msg.host().getBytes();
				ByteBuf byteBuf = outsideServerChannelFuture.channel().alloc().directBuffer();
				
				outsideServerChannelFuture.channel().writeAndFlush(NettyProxyConnectMessageCodec.EncoderUtil.encode(byteBuf, hostBytes, (short) msg.port()));
			}
		});
	}
	
	private static class PrepareConnectChannelHandler extends ChannelInboundHandlerAdapter {
		
		private ChannelHandlerContext browserCtx;
		private SocksCmdRequest req;
		public PrepareConnectChannelHandler(ChannelHandlerContext browserCtx, SocksCmdRequest req) {
			this.browserCtx = browserCtx;
			this.req = req;
		}

		@Override
	    public void channelRead(ChannelHandlerContext outsideProxyCtx, Object msg) throws Exception {
			
			ByteBuf byteBuf = (ByteBuf) msg; // msg-value.type = NettyProxyBuildSuccessMessage
	        
	        // 收到对端的BuildSuccessMessage，说明Proxy已经和目标服务器建立连接成功
	        if (byteBuf.readInt() != Constant.MAGIC_NUMBER || byteBuf.readInt() != NettyProxyBuildSuccessMessage.SUCCESS) {
	        	outsideProxyCtx.close();
	        	browserCtx.close();
	        	return ;
	        }

	        outsideProxyCtx.pipeline().remove(this);
	        logger.info("outside-server connect server [{}:{}] successfully", req.host(), req.port());
	        
	        Channel browserChannel = browserCtx.channel();

	        if (req.port() == 443) { 
	        	outsideProxyCtx.pipeline().addLast(new ForwardHandler("outside_server->browser", browserChannel), new InactiveHandler(browserChannel));
	        	browserChannel.pipeline().addLast(new ForwardHandler("browser->ouside_server", outsideProxyCtx.channel()));
				// 与服务端建立连接完成后，告知浏览器Connect成功，可以进行ssl通信了
				browserCtx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4));
	        	return ;
	        } 
	        
	        // proxy.response -> browser (仅开启单项转发就够了，因为HTTP是请求/应答协议)
	        outsideProxyCtx.pipeline().addLast(new DynamicLengthDecoder(), new DecryptPipeChannelHandler(browserChannel), new InactiveHandler(browserChannel));
	        browserCtx.pipeline().addLast(new EncryptPipeChannelHandler(outsideProxyCtx.channel()), new InactiveHandler(outsideProxyCtx.channel()));
			// 与proxy-server握手完成后，告知browser socks协议结束，后面可以开始发送真正数据了(为了保证数据传输正确性，flush最好还是放到后面)
			browserCtx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4));
		}
	}
}