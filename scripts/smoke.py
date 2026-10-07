#!/usr/bin/env python3
"""HTTP regression checks against Docker/MySQL or the local five-service test harness."""
import json,os,uuid,urllib.request,urllib.error,http.cookiejar,concurrent.futures
BASE=os.getenv('APP_URL','http://localhost:8091').rstrip('/')
PORTS={'utenti':8101,'pagamento':8102,'luce':8103,'luce-business':8104,'gas':8105}
class Client:
 def __init__(self):self.client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 def call(self,service,path,body=None,method=None,expected=200,key=None):
  url=(f'http://127.0.0.1:{PORTS[service]}/api' if os.getenv('LOCAL_SERVICES') else BASE+'/api/'+service)+path
  headers={'Content-Type':'application/json','X-Requested-With':'BollettaLAB'}
  if key:headers['X-Idempotency-Key']=key
  req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method or ('POST' if body is not None else 'GET'))
  try:r=self.client.open(req,timeout=45);status=r.status;data=r.read()
  except urllib.error.HTTPError as e:status=e.code;data=e.read()
  assert status==expected,f'{service}{path}: expected {expected}, got {status}: {data[:400]}'
  return json.loads(data) if data.startswith((b'{',b'[')) else data

def registered(company=False):
 c=Client();em='smoke-'+uuid.uuid4().hex+'@example.test';password='PasswordSmoke123!';b={'email':em,'password':password,'name':'Titolare Test' if company else 'Privato Test','type':'COMPANY' if company else 'PRIVATE'}
 if company:b.update(companyName='Impresa Smoke',vatNumber='12345678901',address='Via Test 1')
 c.call('utenti','/auth/register',b);identity=c.call('utenti','/auth/login',{'email':em,'password':password});c.call('pagamento','/checkout',{'product':'BASE','requestKey':str(uuid.uuid4())});assert c.call('pagamento','/wallet')['remaining']==80
 return c,identity

def run():
 anonymous=Client();anonymous.call('luce','/offerte',expected=401)
 private,identity=registered();other,_=registered();owner,company=registered(True)
 agents=[]
 for n in range(2):
  em=f'agent-{uuid.uuid4().hex}@example.test';invite=owner.call('utenti','/company/agents',{'email':em,'name':f'Agente {n+1}'})
  token=invite['inviteUrl'].split('token=')[1];agent=Client();agent.call('utenti','/auth/accept-invite',{'token':token,'password':'PasswordSmoke123!'});agent.call('utenti','/auth/login',{'email':em,'password':'PasswordSmoke123!'});agents.append(agent)
 owner.call('utenti','/company/agents',{'email':'third-'+uuid.uuid4().hex+'@example.test','name':'Terzo'},expected=409)
 for service in ['luce','luce-business']:
  offer=private.call(service,'/offerte',{'nomeFornitore':'Test','nomeOfferta':'Fisso','tipoOfferta':'PREZZO_FISSO','tipoTariffa':'MONORARIA','prezzi':{'f0':'0.1'},'pcvAnnuo':'120','attiva':True},expected=201)
  params={'nomeProfilo':'Test','fonte':'Fixture sintetica','coefficientePerdite':'0','arrotondaPerdite':False,**{k:'0' for k in ['dispacciamentoKwh','trasportoFissoMese','trasportoPotenzaAnno','trasportoKwh','oneriFissiMese','oneriKwh','accisaKwh']}}
  private.call(service,'/parametri',params,method='PUT')
  bill={'cliente':'Cliente sintetico','pod':'IT001E12345678','fornitore':'Test','potenzaKw':'3','totaleFatturato':'200','aliquotaIva':'0.1','altrePartiteImponibili':'0','altrePartiteEsenti':'0','mesi':[{'mese':'2026-01','f1':'100','f2':'200','f3':'300'}]}
  if service=='luce-business':bill.update(partitaIva='12345678901');bill['mesi'][0]['quoteFisse']=1
  saved=private.call(service,'/bollette',bill,expected=201);key=str(uuid.uuid4());simulation=private.call(service,'/confronti',{'bollettaId':saved['id'],'offertaId':offer['id']},expected=201,key=key)
  assert float(simulation['dati']['risultato']['totale'])==77
  repeated=private.call(service,'/confronti',{'bollettaId':saved['id'],'offertaId':offer['id']},expected=201,key=key);assert repeated['id']==simulation['id']
  other.call(service,'/confronti/'+str(simulation['id']),expected=404)
  assert private.call(service,'/confronti/'+str(simulation['id'])+'/pdf').startswith(b'%PDF-')
 assert private.call('pagamento','/wallet')['remaining']==78
 gas=agents[0].call('gas','/esempi/GENNAIO-FEBBRAIO',{},expected=201)
 request={'billId':gas['bill']['id'],'offerId':gas['offer']['id'],'profileId':gas['profiles'][0]['id']}
 result=agents[0].call('gas','/confronti',request,expected=201,key=str(uuid.uuid4()));assert result['data']['result']['total']=='392.61'
 assert owner.call('pagamento','/wallet')['remaining']==79
 assert len(owner.call('pagamento','/history'))==1 and len(agents[0].call('pagamento','/history'))==1 and agents[1].call('pagamento','/history')==[]
 agents[1].call('gas','/confronti/'+str(result['id']),expected=404)
 assert owner.call('gas','/confronti/'+str(result['id'])+'/pdf').startswith(b'%PDF-')
 rows=owner.call('utenti','/company/agents')['agents'];first=next(r for r in rows if r['name']=='Agente 1');owner.call('utenti','/company/agents/'+first['id'],{'blocked':True},method='PATCH');agents[0].call('utenti','/auth/me',expected=401)
 print('HTTP smoke passed: login, 3 simulators, PDF, idempotency, company quota, two agents, cross-account/agent isolation, session revocation',flush=True)
if __name__=='__main__':run()
