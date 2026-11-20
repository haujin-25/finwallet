package finwallet_api;

import finwallet_api.transfer.risk.TransferPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TransferPolicyProperties.class)
public class FinwalletApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinwalletApiApplication.class, args);
	}

}
