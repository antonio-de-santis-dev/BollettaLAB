package it.bollettalab.pagamento;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(scanBasePackages={"it.bollettalab.pagamento","it.bollettalab.platform"})
public class PaymentsApplication { public static void main(String[] args){SpringApplication.run(PaymentsApplication.class,args);} }
