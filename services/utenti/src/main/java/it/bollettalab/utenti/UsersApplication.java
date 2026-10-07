package it.bollettalab.utenti;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"it.bollettalab.utenti","it.bollettalab.platform"})
public class UsersApplication { public static void main(String[] args){ SpringApplication.run(UsersApplication.class,args); } }
