package it.bollettalab.utenti;
import it.bollettalab.platform.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class UsersApi {
 final Accounts accounts; final InternalClient client; final Environment env;
 UsersApi(Accounts a,InternalClient c,Environment e){accounts=a;client=c;env=e;}
 String payments(){return env.getProperty("platform.payment-url");}
 @GetMapping("/api/auth/config") Object config(){return Map.of("development",accounts.dev());}
 @PostMapping("/api/auth/register") Object register(@RequestBody Map<String,Object> b){return accounts.register(b);}
 @PostMapping("/api/auth/login") Identity login(@RequestBody Map<String,Object> b,HttpServletRequest r,HttpServletResponse s){return accounts.login(b,r,s);}
 @GetMapping("/api/auth/me") Identity me(){return RequestContext.required();}
 @PostMapping("/api/auth/logout") Object logout(HttpServletRequest req,HttpServletResponse res){if(req.getCookies()!=null)for(Cookie c:req.getCookies())if(c.getName().equals("BL_SESSION"))accounts.jdbc().update("DELETE FROM sessions WHERE digest=?",Tokens.hash(c.getValue()));accounts.cookie(res,"");return Map.of("message","Disconnesso");}
 @PostMapping("/api/auth/forgot-password") Object forgot(@RequestBody Map<String,Object> b,HttpServletRequest req){accounts.throttle(req.getRemoteAddr());return accounts.forgot(b);}
 @PostMapping("/api/auth/reset-password") Object reset(@RequestBody Map<String,Object> b){accounts.reset(b);return Map.of("message","Password aggiornata. Accedi con la nuova password");}
 @PostMapping("/api/auth/verify-email") Object verify(@RequestBody Map<String,Object> b){accounts.verify(Accounts.str(b,"token"));return Map.of("message","Email verificata");}
 @PostMapping("/api/auth/accept-invite") Object accept(@RequestBody Map<String,Object> b){accounts.accept(b);return Map.of("message","Account agente attivato. Ora puoi accedere");}
 @GetMapping("/internal/identity") Identity internalIdentity(HttpServletRequest req){return accounts.authenticate(req);}
 @PostMapping("/internal/workspaces/{id}/activate") @Transactional Object activate(@PathVariable String id){Integer verified=accounts.jdbc().queryForObject("SELECT COUNT(*) FROM accounts WHERE workspace_id=? AND role IN ('PRIVATE','OWNER') AND verified=TRUE",Integer.class,id);if(verified==null||verified==0)throw new HttpProblem(409,"Verifica prima l'email dell'account");var ws=accounts.jdbc().queryForList("SELECT status FROM workspaces WHERE id=? FOR UPDATE",id);if(ws.isEmpty())throw new HttpProblem(404,"Account non trovato");if(!Set.of("ACTIVE","PENDING_PAYMENT").contains(Accounts.str(ws.get(0),"status")))throw new HttpProblem(409,"Account bloccato");accounts.jdbc().update("UPDATE workspaces SET status='ACTIVE' WHERE id=?",id);return Map.of("activated",true);}
 @GetMapping("/api/company/agents") Object agents(){RequestContext.owner();String w=RequestContext.required().workspaceId();return Map.of("agents",accounts.jdbc().queryForList("SELECT id,name,email,status,created_at FROM accounts WHERE workspace_id=? AND role='AGENT' ORDER BY name",w),"invites",accounts.jdbc().queryForList("SELECT email,name,expires_at FROM account_tokens WHERE workspace_id=? AND purpose='INVITE' AND expires_at>CURRENT_TIMESTAMP",w));}
 @PostMapping("/api/company/agents") Object invite(@RequestBody Map<String,Object> b){RequestContext.owner();Map<?,?> access=client.get(payments()+"/internal/access/"+RequestContext.required().workspaceId(),Map.class);if(!Boolean.TRUE.equals(access.get("active")))throw new HttpProblem(402,"Attiva prima il piano impresa");return accounts.invite(b,((Number)access.get("agentSeats")).intValue());}
 @PatchMapping("/api/company/agents/{id}") Object agentState(@PathVariable String id,@RequestBody Map<String,Object> b){accounts.block(id,Boolean.TRUE.equals(b.get("blocked")),false);return Map.of("updated",true);}
 @GetMapping("/api/admin/users") Object users(){RequestContext.admin();return accounts.jdbc().queryForList("SELECT a.id,a.workspace_id,a.name,a.email,a.role,a.status,a.verified,a.created_at,w.name company_name,w.status workspace_status FROM accounts a JOIN workspaces w ON w.id=a.workspace_id ORDER BY a.created_at DESC");}
 @GetMapping("/api/admin/logs") Object logs(){RequestContext.admin();return accounts.jdbc().queryForList("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT 300");}
 @PatchMapping("/api/admin/users/{id}") Object state(@PathVariable String id,@RequestBody Map<String,Object> b){accounts.block(id,Boolean.TRUE.equals(b.get("blocked")),true);return Map.of("updated",true);}
 @PostMapping("/api/admin/users/{id}/reset-password") Object resetUser(@PathVariable String id){RequestContext.admin();var user=accounts.account(id);String link=accounts.issue("RESET",id,Accounts.str(user,"workspace_id"),Accounts.str(user,"email"),Accounts.str(user,"name"));accounts.audit("ADMIN_RESET_PASSWORD",id);return accounts.dev()?Map.of("message","Link di prova generato","resetUrl",link):Map.of("message","Link di reimpostazione inviato all'utente");}
 @DeleteMapping("/api/admin/users/{id}") Object delete(@PathVariable String id){RequestContext.admin();var user=accounts.account(id);if("ADMIN".equals(Accounts.str(user,"role")))throw new HttpProblem(403,"Non puoi eliminare un amministratore");String w=Accounts.str(user,"workspace_id");if("AGENT".equals(Accounts.str(user,"role"))){accounts.jdbc().update("DELETE FROM accounts WHERE id=?",id);accounts.audit("DELETE_AGENT",id);return Map.of("deleted",true);}accounts.jdbc().update("UPDATE workspaces SET status='DELETING' WHERE id=?",w);for(String service:List.of("luce","luce-business","gas","pagamento"))client.delete("http://"+service+":8080/internal/workspaces/"+w);accounts.jdbc().update("DELETE FROM account_tokens WHERE workspace_id=?",w);accounts.jdbc().update("DELETE FROM accounts WHERE workspace_id=?",w);accounts.jdbc().update("DELETE FROM workspaces WHERE id=?",w);accounts.audit("DELETE_WORKSPACE",w);return Map.of("deleted",true);}
}
