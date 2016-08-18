package sample.web.reactive;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ebay.kernel.cal.api.CalStreamGroup;
import com.ebay.kernel.cal.api.CalStreamUtils;
import com.ebay.kernel.cal.api.CalTransaction;
import com.ebay.squbs.rocksqubs.kafka.consumer.japi.KafkaConsumerSettings;
import com.ebay.squbs.rocksqubs.kafka.producer.japi.KafkaProducerSettings;
import com.ebay.squbs.rocksqubs.kafka.resolver.BaseKafkaResolver$;
import com.ebay.squbs.rocksqubs.kafka.resolver.KafkaRegistry;
import com.ebayinc.platform.services.EndPoint;
import com.paypal.platform.rxnetty.client.http.client.RaptorRxNettyHttpClient;
import com.paypal.platform.rxnetty.client.spi.AsyncEndPoint;

import akka.actor.ActorSystem;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.scaladsl.Consumer;
import akka.kafka.scaladsl.Producer;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.scaladsl.Keep;
import akka.stream.scaladsl.Sink;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;
import rx.schedulers.Schedulers;

/**
 * @author qbradley
 *
 */
@RestController
@RequestMapping(value = "/home")
public class HomeController {
	private static Logger logger = LoggerFactory.getLogger(HomeController.class);

	@Inject
	ActorSystem system;
	final Properties props = new Properties();
	ActorMaterializer materializer;
	ProducerSettings<byte[], String> producerSettings;
	ConsumerSettings<byte[], String> consumerSettings;

	@Inject
	@EndPoint(service = "self")
	private WebTarget self;

	@Inject
	@AsyncEndPoint(service = "calendarserv")
	private RaptorRxNettyHttpClient calendarserv;

	@PostConstruct
	public void init() {
		try (InputStream is = this.getClass().getResourceAsStream("/kafka.properties")) {
			props.load(is);
		} catch (Exception e) {
			// do nothing
		}
		KafkaRegistry.get(system).register(BaseKafkaResolver$.MODULE$);
		materializer = ActorMaterializer.create(system);
		producerSettings = KafkaProducerSettings.get("kafka-multi", new ByteArraySerializer(),
				new StringSerializer(), Optional.of(props), system);
		consumerSettings = KafkaConsumerSettings.get("kafka-multi", new ByteArrayDeserializer(),
				new StringDeserializer(), Optional.of(props), system);
	}

	@RequestMapping(value = "/")
	public Mono<BootStarter> starter() {
		return Mono.just(new BootStarter("spring-boot-starter-web-reactive", "Spring Boot Web Reactive"));
	}

	@RequestMapping(value = "/observable/{msg}")
	public Observable<String> observable(@PathVariable String msg) {
		return Observable.just(msg).repeat(100000);
	}

	@RequestMapping(value = "/akka")
	public Observable<String> akka(@RequestAttribute(value = "CSG") Object requestAttribute) {
		final CalTransaction calTransaction = ((CalStreamGroup) requestAttribute).getPrimaryCalStream()
				.asyncTransaction("AsyncCb", "BIZ").setStatus("0");
		try {
			Source.range(1, 200)
					.map(elem -> new ProducerRecord<byte[], String>("java", String.valueOf(elem)))
					.to(Producer.plainSink(producerSettings).asJava()).run(materializer);
			// reactive kafka consuming, connect to Source
			final Publisher<ConsumerRecord<byte[], String>> publisher = Consumer
					.plainSource(consumerSettings, Subscriptions.topics("java"))
					.toMat(Sink.asPublisher(false), Keep.right()).run(materializer);

			// Publisher --> Observable
			return RxReactiveStreams.toObservable(publisher).map(msg -> {
				String message = msg.value();
				logger.info(message);
				return message;
			}).take(200).subscribeOn(Schedulers.computation());
		} finally {
			calTransaction.completed();
		}
	}

	@RequestMapping(value = "/aeroclient")
	public CompletionStage<String> aeroclient(@RequestAttribute(value = "CSG") Object requestAttribute) {
		final CalTransaction calTransaction = ((CalStreamGroup) requestAttribute).getPrimaryCalStream()
				.asyncTransaction("AsyncCb", "BIZ").setStatus("0");
		try {
			return CompletableFuture.supplyAsync(() -> {
				CalTransaction calTransaction2 = calTransaction.calStream()
						.asyncTransaction("AsyncCb", "AEROCLIENT").setStatus("0");
				CalStreamUtils.getInstance().installAsyncStreamAsSync(calTransaction2.calStream());
				try {
					char[] chars = new char[100000];
					Arrays.fill(chars, 'X');
					String response = String.valueOf(
							self.path("/home/flux").request().accept(MediaType.TEXT_PLAIN)
									.post(Entity.text(new String(chars)))
									.readEntity(String.class).length());
					logger.info(response);
					return response;
				} finally {
					CalStreamUtils.getInstance().extractSyncStreamAsAsync();
					calTransaction2.completed();
				}
			});
		} finally {
			calTransaction.completed();
		}
	}

	@RequestMapping(value = "/rxnetty")
	public Observable<String> rxnetty(@RequestAttribute(value = "CSG") Object requestAttribute) {
		final CalTransaction calTransaction = ((CalStreamGroup) requestAttribute).getPrimaryCalStream()
				.asyncTransaction("AsyncCb", "BIZ").setStatus("0");
		CalStreamUtils.getInstance().installAsyncStreamAsSync(calTransaction.calStream());
		try {
			return calendarserv
					.createHystrixObservable(HttpClientRequest.createGet("/v1/calendar/workdays/")
							.withHeader("counrty", "US")
							.withContent(MediaType.APPLICATION_JSON))
					.map(response -> response.getResponseCode().toString() + '\n'
							+ new String(response.getContent().toByteArray()));
		} finally {
			CalStreamUtils.getInstance().extractSyncStreamAsAsync();
			calTransaction.completed();
		}
	}

	@PostMapping(path = "/flux", consumes = "text/plain", produces = "text/plain")
	public Flux<String> flux(@RequestBody String msgs) {
		return Flux.just(msgs);
	}
}