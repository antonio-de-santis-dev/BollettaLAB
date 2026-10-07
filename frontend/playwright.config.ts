import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e',timeout:60000,workers:1,use:{baseURL:process.env.APP_URL??'http://localhost:8091',headless:true,trace:'retain-on-failure',screenshot:'only-on-failure'},reporter:[['list'],['html',{open:'never'}]]});
