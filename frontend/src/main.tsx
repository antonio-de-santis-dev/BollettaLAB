import "./modules/luce/styles.css";
import "./modules/luce-business/styles.css";
import "./modules/luce-business/business/business.css";
import "./modules/gas/styles.css";
import "./modules/gas/gas.css";
import "./modules/gas/gas-fonti.css";
import React,{Component,ReactNode} from 'react';import ReactDOM from 'react-dom/client';import{BrowserRouter}from'react-router-dom';import App from './App';import{AuthProvider}from './platform';import './platform.css';
class Boundary extends Component<{children:ReactNode},{failed:boolean}>{state={failed:false};static getDerivedStateFromError(){return{failed:true};}render(){return this.state.failed?<main className="lab-page"><h1>Non è stato possibile aprire questa pagina.</h1><p>Ricarica per riprovare. Le simulazioni già salvate restano nello storico.</p><button className="lab-btn" onClick={()=>window.location.reload()}>Ricarica</button></main>:this.props.children;}}
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><Boundary><BrowserRouter><AuthProvider><App/></AuthProvider></BrowserRouter></Boundary></React.StrictMode>);
