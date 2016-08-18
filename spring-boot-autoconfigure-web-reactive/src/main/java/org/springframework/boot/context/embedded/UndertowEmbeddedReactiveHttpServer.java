package org.springframework.boot.context.embedded;

import java.net.InetAddress;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.UndertowHttpHandlerAdapter;

import com.ebay.raptor.configweb.console.ConsoleFrontController;
import com.ebay.raptor.configweb.log.RaptorContentReader;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

/**
 * @author Brian Clozel
 */
public class UndertowEmbeddedReactiveHttpServer extends AbstractEmbeddedReactiveHttpServer
		implements EmbeddedReactiveHttpServer {

	private final String DEFAULT_HOST = "0.0.0.0";

	private Undertow server;

	private DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

	private boolean running;

	@Override
	public void afterPropertiesSet() throws Exception {
		DeploymentInfo servletBuilder = Servlets.deployment().setClassLoader(this.getClass().getClassLoader())
				.setContextPath("/").setDeploymentName("mainserver")
				.addServlets(Servlets.servlet(ConsoleFrontController.class)
						.addMapping("/admin/v3console/*").addMapping("/admin/js/*")
						.addMapping("/admin/css/*").addMapping("/admin/images/*")
						.addMapping("/admin/hystrix-dashboard.html")
						.addMapping("/admin/monitor/*").addMapping("/admin/components/*"),
						Servlets.servlet(RaptorContentReader.class).addMapping("/raptorlog/*"))
				.addInitialHandlerChainWrapper(new HandlerWrapper() {
					@Override
					public HttpHandler wrap(final HttpHandler handler) {
						return Handlers.path()
								.addPrefixPath("/home", new UndertowHttpHandlerAdapter(
										getHttpHandler(), dataBufferFactory))
								.addPrefixPath("/", handler);
					}
				});
		DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
		manager.deploy();
		this.server = Undertow.builder().addHttpListener(getPort(), determineHost()).setHandler(manager.start())
				.build();
	}

	private String determineHost() {
		InetAddress address = getAddress();
		if (address == null) {
			return DEFAULT_HOST;
		} else {
			return address.getHostAddress();
		}
	}

	@Override
	public void start() {
		if (!this.running) {
			this.server.start();
			this.running = true;
		}

	}

	@Override
	public void stop() {
		if (this.running) {
			this.server.stop();
			this.running = false;
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}
}
