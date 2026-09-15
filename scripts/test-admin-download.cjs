const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../apps/admin/src/utils/ruoyi.js'),'utf8').replace(/^import .*$/gm,'').replace(/export function /g,'function ');
(async()=>{
  const calls=[],messages=[];let clicked=0,removed=0,revoked=0;
  const response={headers:{'content-type':'application/octet-stream'},data:{synthetic:true}};
  const sandbox={process:{env:{VUE_APP_BASE_API:'/api'}},axios:{get:(url,config)=>{calls.push({url,config});return Promise.resolve(response);}},getToken:()=> 'synthetic-token',Message:{error:m=>messages.push(m)},URL:{createObjectURL:()=> 'blob:synthetic',revokeObjectURL:()=>revoked++},document:{createElement:()=>({click:()=>clicked++,remove:()=>removed++}),body:{appendChild:()=>{}}},setTimeout:fn=>fn()};
  vm.createContext(sandbox);vm.runInContext(source,sandbox);
  await sandbox.download('report & summary.txt');
  assert.equal(calls[0].config.headers.Authorization,'Bearer synthetic-token');
  assert.equal(calls[0].config.params.fileName,'report & summary.txt');
  assert.equal(calls[0].config.params.delete,undefined);assert.equal(clicked,1);assert.equal(removed,1);assert.equal(revoked,1);
  response.headers['content-type']='application/json';
  await assert.rejects(sandbox.download('denied.txt'));assert.equal(clicked,1);assert.equal(messages.length,1);
  console.log('Admin download: authenticated request, safe parameters, no GET deletion, and JSON error rejection passed');
})().catch(error=>{console.error(error);process.exitCode=1;});
