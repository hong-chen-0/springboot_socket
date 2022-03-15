package springboot0216;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
@SpringBootApplication
public class SocketServerApplication {

	//WebSocket
	@Bean
	public ServerEndpointExporter serverEndpointExporter() {
	    return new ServerEndpointExporter();
	}
	
	public static void main(String[] args) {
		SpringApplication.run(SocketServerApplication.class, args);
	}

}
