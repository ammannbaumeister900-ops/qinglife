const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'../../..');
const Ajv=require(require.resolve('ajv',{paths:[fs.realpathSync(path.join(root,'apps/admin/node_modules/eslint'))]}));const ajv=new Ajv({allErrors:true});
const evidence=path.resolve(process.env.QINGLIFE_M2_EVIDENCE_DIR||path.join(root,'.tools/m2-contract-validation'));fs.mkdirSync(evidence,{recursive:true});
const uuid={type:'string',pattern:'^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$'},money={type:'string',pattern:'^(?:0|[1-9][0-9]{0,9})\\.[0-9]{2}$'},positiveMoney={...money,not:{const:'0.00'}},reason={type:'string',minLength:1,maxLength:500,pattern:'\\S'},revision={type:'integer',minimum:0};
const object=(properties,required=Object.keys(properties))=>({type:'object',additionalProperties:false,properties,required});
const common={operationId:uuid,expectedRevision:revision};
const schemas={
 action:object({...common,reason}),
 capture:object({requestId:uuid,captureOnly:{const:true},amount:{...positiveMoney,pattern:'^(?:0|[1-9][0-9]{0,7})\\.[0-9]{2}$'},paymentMethod:{enum:['cash','wechat_scan','alipay_scan','transfer','other']},receivedConfirmed:{const:true},receiptReference:{type:['string','null'],maxLength:100},evidence:reason}),
 allocate:object({...common,receiptId:uuid,expectedReceiptRevision:revision,lines:{type:'array',minItems:1,maxItems:50,items:object({registrationId:uuid,amount:positiveMoney})},reason}),
 settle: {oneOf:[object({...common,mode:{const:'participant'},settlementType:{const:'money'},allocationIds:{type:'array',minItems:1,maxItems:100,uniqueItems:true,items:uuid},finalAmount:money,differenceConfirmed:{type:'boolean'},reason}),object({...common,mode:{const:'participant'},settlementType:{const:'pass'},passAccountId:uuid,verificationId:uuid,passUnits:{type:'integer',minimum:1,maximum:65535},reason})]},
 refund:object({...common,requestedAmount:positiveMoney,allocations:{type:'array',minItems:1,maxItems:100,items:object({allocationId:uuid,amount:positiveMoney})},reason}),
 refundActual:object({...common,actualAmount:positiveMoney,paymentMethod:{enum:['cash','wechat_scan','alipay_scan','transfer','other']},externalReference:{type:['string','null'],minLength:1,maxLength:100},evidence:reason,occurredAt:{type:'string',pattern:'^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\+08:00$'},reason}),
 verifyPass:object({...common,registrationId:uuid,passAccountId:uuid,scope:{enum:['self','same_order']},passUnits:{type:'integer',minimum:1,maximum:65535},basis:{...reason,maxLength:600}})
};
// The service requires this only when finalAmount differs from the stored quote.
schemas.settle.oneOf[0].properties.differenceReason=reason;
schemas.action.properties.noActualPaymentConfirmed={type:'boolean'};
const contracts={draftOnly:true,implemented:false,writeFlag:'qinglife.finance.participant_write_enabled=false',authentication:'operator session; operator/holder/beneficiary resolved by server, never trusted from body',schemas,routes:[['GET','/life/registration/batch/{id}/finance','read'],['POST','/life/registration/{id}/offline-receipt','capture'],['PUT','/life/registration/{id}/refund-requests/{requestId}/processing','action'],['PUT','/life/registration/{id}/refund-requests/{requestId}/cancel','action'],['PUT','/life/registration/{id}/refund-requests/{requestId}/fail','action'],['PUT','/life/registration/{id}/settlement/{settlementId}/revoke','action'],['POST','/life/registration/batch/{id}/receipt-allocations','allocate'],['PUT','/life/registration/{id}/settlement','settle'],['POST','/life/registration/{id}/refund-requests','refund'],['POST','/life/registration/{id}/refund-requests/{requestId}/payments','refundActual'],['POST','/life/registration/{id}/pass-verifications','verifyPass']],semanticChecks:['same order/session for every allocation','confirmed actual receipt, no excess alloc/reversal','allocation total versus receivable and explicit amount difference acknowledgement','active refund reservation plus completed attribution <= refundable allocation','refund never updates registration_status','operator permission plus verified holder and same order beneficiary','lock and unique operation payload hash; revision and transaction','old endpoints reject participant writes; write flag defaults off']};
contracts.routes.push(['PUT','/life/registration/{id}/cancel','action']);
assert.deepEqual(JSON.parse(fs.readFileSync(path.join(__dirname,'M2-contracts.json'),'utf8')),contracts,'Versioned contracts must match the reviewed schemas');
const id='00000000-0000-4000-8000-000000000001',c={operationId:id,expectedRevision:0},checks=[];
function check(name,value,expected){assert.equal(ajv.compile(schemas[name])(value),expected,name+' '+JSON.stringify(value));checks.push({schema:name,expected})}
check('allocate',{...c,receiptId:id,expectedReceiptRevision:0,lines:[{registrationId:id,amount:'80.00'}],reason:'synthetic'},true);
for(const amount of ['-1.00','0.00','0.001',80,'1e2','10000000000.00'])check('allocate',{...c,receiptId:id,expectedReceiptRevision:0,lines:[{registrationId:id,amount}],reason:'synthetic'},false);
check('allocate',{...c,receiptId:id,expectedReceiptRevision:0,lines:[],reason:'synthetic'},false);check('allocate',{...c,receiptId:id,expectedReceiptRevision:0,lines:Array(51).fill({registrationId:id,amount:'1.00'}),reason:'synthetic'},false);
const cash={...c,mode:'participant',settlementType:'money',allocationIds:[id],finalAmount:'80.00',differenceConfirmed:true,reason:'synthetic'};check('settle',cash,true);check('settle',{...cash,passAccountId:id},false);check('settle',{...cash,allocationIds:[id,id]},false);
check('settle',{...cash,differenceReason:'verified price difference'},true);
check('settle',{...cash,differenceReason:' '},false);
const pass={...c,mode:'participant',settlementType:'pass',passAccountId:id,verificationId:id,passUnits:1,reason:'synthetic'};check('settle',pass,true);check('settle',{...pass,allocationIds:[id]},false);check('settle',{...pass,passAccountIds:[id,id]},false);check('settle',{...pass,operatorId:9},false);
check('refund',{...c,requestedAmount:'10.00',allocations:[{allocationId:id,amount:'10.00'}],reason:'synthetic'},true);check('refund',{...c,requestedAmount:'10.00',allocations:[{allocationId:id,amount:'10.00'}],reason:'synthetic',cancelRegistration:true},false);
const actual={...c,actualAmount:'10.00',paymentMethod:'cash',externalReference:null,evidence:'synthetic proof',occurredAt:'2026-10-15T20:00:00+08:00',reason:'synthetic'};check('refundActual',actual,true);check('refundActual',{...actual,actualAmount:'0.00'},false);
const verify={...c,registrationId:id,passAccountId:id,scope:'same_order',passUnits:1,basis:'synthetic operator verification'};check('verifyPass',verify,true);check('verifyPass',{...verify,basis:' '},false);check('verifyPass',{...verify,operatorId:9},false);
check('capture',{requestId:id,captureOnly:true,amount:'100.00',paymentMethod:'cash',receivedConfirmed:true,receiptReference:null,evidence:'synthetic'},true);
check('capture',{requestId:id,captureOnly:true,amount:'100.00',paymentMethod:'cash',receivedConfirmed:false,receiptReference:null,evidence:'synthetic'},false);
check('capture',{requestId:id,captureOnly:true,amount:'0.00',paymentMethod:'cash',receivedConfirmed:true,receiptReference:null,evidence:'synthetic'},false);
check('action',{...c,reason:'synthetic'},true);check('action',{...c,reason:'synthetic',operatorId:9},false);
check('action',{...c,reason:'synthetic',noActualPaymentConfirmed:true},true);
check('action',{...c,reason:'synthetic',noActualPaymentConfirmed:'true'},false);
fs.writeFileSync(path.join(evidence,'M2-contract-test-results.json'),JSON.stringify({checks,total:checks.length,passed:checks.length,realHttp:false,production:false,semanticServiceImplementation:false},null,2));console.log('Contract schema checks passed:',checks.length);
