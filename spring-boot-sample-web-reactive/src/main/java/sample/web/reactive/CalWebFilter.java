package sample.web.reactive;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.ebay.kernel.cal.api.CalTransaction;
import com.ebay.kernel.cal.api.sync.CalEventFactory;
import com.ebay.kernel.cal.api.sync.CalTransactionFactory;

import reactor.core.publisher.Mono;

/**
 * @author qbradley
 *
 */
@Component
public class CalWebFilter implements WebFilter {
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String name = exchange.getRequest().getURI().getPath();
		CalTransaction calTransaction = CalTransactionFactory.create("URL").setName(name).setStatus("0");
		try {
			exchange.getAttributes().putIfAbsent("CSG", calTransaction.calStream().group());
			CalEventFactory.create("Reactive").setName("Before-" + name).completed();
			return chain.filter(exchange);
		} catch (Throwable th) {
			calTransaction.setStatus(th);
			throw th;
		} finally {
			CalEventFactory.create("Reactive").setName("After-" + name).completed();
			calTransaction.completed();
		}
	}

}