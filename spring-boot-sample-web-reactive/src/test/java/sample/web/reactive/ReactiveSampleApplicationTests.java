package sample.web.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.client.reactive.ClientWebRequestBuilders.get;
import static org.springframework.web.client.reactive.ResponseExtractors.response;
import static reactor.test.TestSubscriber.subscribe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.reactive.WebClient;

import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReactiveSampleApplicationTests {

	private WebClient webClient;

	@LocalServerPort
	private int port;

	@Before
	public void setup() {
		this.webClient = new WebClient(new ReactorClientHttpConnector());
	}

	@Test
	public void homeController() {

		Mono<ResponseEntity<BootStarter>> result = this.webClient
				.perform(get("http://localhost:" + this.port + "/").accept(MediaType.APPLICATION_JSON))
				.extract(response(BootStarter.class));

		subscribe(result).awaitAndAssertNextValuesWith(
				response -> {
					assertThat(response.getStatusCode().value()).isEqualTo(200);
					assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_UTF8);

					BootStarter starter = response.getBody();
					assertThat(starter.getId()).isEqualTo("spring-boot-starter-web-reactive");
					assertThat(starter.getLabel()).isEqualTo("Spring Boot Web Reactive");
				}).assertComplete();
	}

}