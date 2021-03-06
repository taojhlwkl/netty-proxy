package org.hum.nettyproxy.common.core.config;

import java.util.ArrayList;
import java.util.List;

import org.hum.nettyproxy.common.enumtype.RunModeEnum;
import org.hum.nettyproxy.compoment.interceptor.model.InterceptorRegx;

import lombok.Data;

@Data
public class NettyProxyConfig {

	/**
	 * 运行模式：根据枚举选择程序做什么样的转发
	 * 启动参数样例：nettyproxy.runmode=11
	 */
	private RunModeEnum runMode;
	/**
	 * 服务启动监听端口，代理程序需要将流量转发到这个端口方可实现转发
	 * 启动参数样例：nettyproxy.port=5432 
	 */
	private Integer port;
	/**
	 * 控制台端口
	 * 启动参数样例：nettyproxy.consoleport=5431
	 */
	private Integer consolePort;
	/**
	 * Netty中worker线程数量
	 * 启动参数样例：nettyproxy.workercnt=80
	 */
	private int workerCnt;
	/**
	 * 墙外服务器地址，只有runmode=11、12时该参数才生效
	 * 启动参数样例：nettyproxy.outside_proxy_host=57.12.39.152
	 */
	private String outsideProxyHost;
	/**
	 * 墙外服务器端口
	 * 启动参数样例：nettyproxy.outside_proxy_port=33121
	 */
	private Integer outsideProxyPort;
	/**
	 * 网站根目录完整路径
	 * 启动参数样例：nettyproxy.webroot=/home/huming/netty_http_server/webroot
	 */
	private String webroot;
	/**
	 * 开启权限校验（只针对runMode=11即HttpInsideProxy做鉴权，socks鉴权找不到唯一身份标识，只能在建立连接时通过协议自身授权验证）
	 * 启动参数样例：nettyproxy.enableauthority=1
	 */
	private Boolean enableAuthority = false;
	/**
	 * 拦截规则
	 * 1.通过参数直接配置，例如：nettyproxy.intercept-redirect=www.baidu.com->220.181.38.150
	 * 2.通过文件读取，例如：nettyproxy.intercepptor_regx=interceptor_regx.cfg
	 */
	private List<InterceptorRegx> interceptorRegxList;
	
	public NettyProxyConfig() { 
		this.interceptorRegxList = new ArrayList<InterceptorRegx>();
	}
	
	public NettyProxyConfig(RunModeEnum runMode, int port) {
		this.runMode = runMode;
		this.port = port;
	}
	
	public NettyProxyConfig addInterceptRegx(InterceptorRegx interceptorRegx) {
		interceptorRegxList.add(interceptorRegx);
		return this;
	}
}
