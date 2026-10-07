import { createContext, useContext, useEffect, useState, ReactNode, useCallback } from 'react';
import axios from 'axios';
export type User={id:string;workspaceId:string;role:'PRIVATE'|'OWNER'|'AGENT'|'ADMIN';name:string;email:string;status:string;companyName?:string};
export type Wallet={remaining:number;monthlyRemaining:number;extraRemaining:number;agentSeats:number;active:boolean;periodEnd:string;development:boolean};
export const users=axios.create({baseURL:'/api/utenti',headers:{'X-Requested-With':'BollettaLAB'},timeout:30000});
export const payments=axios.create({baseURL:'/api/pagamento',headers:{'X-Requested-With':'BollettaLAB'},timeout:30000});
export function errorMessage(e:unknown){return axios.isAxiosError(e)?e.response?.data?.message??'Servizio non disponibile. Riprova.':e instanceof Error?e.message:'Operazione non riuscita';}
const Auth=createContext<{user:User|null;wallet:Wallet|null;loading:boolean;walletError:string;refresh:()=>Promise<User|null>;refreshWallet:()=>Promise<void>}>({user:null,wallet:null,loading:true,walletError:'',refresh:async()=>null,refreshWallet:async()=>{}});
export const useAuth=()=>useContext(Auth);
export function AuthProvider({children}:{children:ReactNode}){const[user,setUser]=useState<User|null>(null),[wallet,setWallet]=useState<Wallet|null>(null),[loading,setLoading]=useState(true),[walletError,setWalletError]=useState('');
 const refresh=useCallback(async()=>{try{const u=(await users.get<User>('/auth/me')).data;setUser(u);return u;}catch(e){if(axios.isAxiosError(e)&&[401,403].includes(e.response?.status??0))setUser(null);return null;}finally{setLoading(false);}},[]);
 const refreshWallet=useCallback(async()=>{if(!user||user.role==='ADMIN'){setWallet(null);return;}setWalletError('');try{setWallet((await payments.get<Wallet>('/wallet')).data);}catch(e){setWallet(null);setWalletError(errorMessage(e));}},[user]);
 useEffect(()=>{void refresh();},[refresh]);useEffect(()=>{void refreshWallet();window.addEventListener('lab:wallet-changed',refreshWallet);return()=>window.removeEventListener('lab:wallet-changed',refreshWallet);},[refreshWallet]);
 return <Auth.Provider value={{user,wallet,loading,walletError,refresh,refreshWallet}}>{children}</Auth.Provider>;
}
export function useTask(){const[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');return{busy,error,notice,setNotice,run:async(action:()=>Promise<void>)=>{setBusy(true);setError('');setNotice('');try{await action();}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}};}
