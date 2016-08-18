package sample.web.reactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.ebay.raptor.kernel.init.RaptorCoreInitializer;

@SpringBootApplication
public class ReactiveSampleApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReactiveSampleApplication.class, args);
		RaptorCoreInitializer.enableTraffic();
	}
}
