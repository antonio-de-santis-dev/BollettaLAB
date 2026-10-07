package it.bollettalab.pagamento;
import it.bollettalab.platform.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:payments;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","platform.internal-key=01234567890123456789012345678901"})
@ActiveProfiles("test")
class WalletsTest {
 @Autowired Wallets wallets;@MockitoBean InternalClient internal;
 Identity user(){return new Identity(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"PRIVATE","Mario","mario@test.it","ACTIVE",null,null,null);}
 Map<String,Object> request(Identity i,String key){return Map.of("workspaceId",i.workspaceId(),"userId",i.id(),"authorName",i.name(),"simulator","gas","requestKey",key,"hash","a".repeat(64));}
 @Test void activationIdempotentConsumptionAndFailureRefund(){var i=user();when(internal.post(anyString(),any(),eq(Map.class))).thenReturn(Map.of("activated",true));wallets.checkout(i,Map.of("product","BASE","requestKey",UUID.randomUUID().toString()));assertThat(wallets.wallet(i.workspaceId()).get("remaining")).isEqualTo(80);String key=UUID.randomUUID().toString();var reservation=wallets.reserve(request(i,key));assertThat(wallets.wallet(i.workspaceId()).get("remaining")).isEqualTo(79);wallets.complete(reservation.get("id").toString(),Map.of("resultId",1,"response","{\"id\":1}"));assertThat(wallets.reserve(request(i,key)).get("completed")).isEqualTo(true);assertThat(wallets.wallet(i.workspaceId()).get("remaining")).isEqualTo(79);var failed=wallets.reserve(request(i,UUID.randomUUID().toString()));wallets.release(failed.get("id").toString());wallets.release(failed.get("id").toString());assertThat(wallets.wallet(i.workspaceId()).get("remaining")).isEqualTo(79);}
 @Test void parallelRequestsNeverOverspend() throws Exception {var i=user();when(internal.post(anyString(),any(),eq(Map.class))).thenReturn(Map.of("activated",true));wallets.checkout(i,Map.of("product","BASE","requestKey",UUID.randomUUID().toString()));wallets.jdbc().update("UPDATE wallets SET monthly_remaining=1 WHERE workspace_id=?",i.workspaceId());var pool=Executors.newFixedThreadPool(4);AtomicInteger success=new AtomicInteger();var futures=new ArrayList<Future<?>>();for(int n=0;n<4;n++)futures.add(pool.submit(()->{try{wallets.reserve(request(i,UUID.randomUUID().toString()));success.incrementAndGet();}catch(HttpProblem expected){assertThat(expected.status()).isEqualTo(402);}}));for(var f:futures)f.get(10,TimeUnit.SECONDS);pool.shutdown();assertThat(success.get()).isEqualTo(1);assertThat(wallets.wallet(i.workspaceId()).get("remaining")).isEqualTo(0);}
}
