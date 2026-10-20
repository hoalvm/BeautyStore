const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function load() {
    const nodes = new Map(); const tokens = new Map(); const intervals = []; let now=1000000;
    const node = () => ({children:[],events:{},style:{},classList:{add(){},remove(){}},value:'',
        append(...children) {this.children.push(...children);}, appendChild(child) {this.children.push(child);}, replaceChildren(...children) {this.children=children;},
        addEventListener(type,handler) {this.events[type]=handler;},setAttribute(){},focus(){},remove(){},reportValidity:()=>true,
        cloneNode:node,replaceWith(other){this.replacement=other;}});
    const context = {console, URLSearchParams, Date: class extends Date {static now(){return now;}}, window: {},
        setInterval(fn){intervals.push(fn);return intervals.length;},clearInterval(){},setTimeout(){},
        localStorage:{getItem(){return null;}},sessionStorage:{getItem:key=>tokens.get(key),setItem:(key,value)=>tokens.set(key,value),removeItem:key=>tokens.delete(key)},
        document:{head:node(),body:node(),addEventListener(){},createElement:node,getElementById(id){if(!nodes.has(id))nodes.set(id,node());return nodes.get(id);}},
        fetch:async()=>{throw new Error('Unexpected request');}};
    vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/order-confirmation.js'),'utf8'),context);
    return {context,nodes,tokens,intervals,node,advance(ms){now+=ms;intervals.forEach(fn=>fn());}};
}
test('terminal payment statuses do not redirect back to payment pending',()=>{
    const {context}=load();
    for(const paymentStatus of ['PAID','FAILED','CANCELLED','EXPIRED','RECONCILIATION_REQUIRED']) {
        assert.equal(context.isAwaitingPayment({status:'PENDING_PAYMENT',paymentMethod:'VNPAY',paymentStatus}),false);
    }
    assert.equal(context.isAwaitingPayment({status:'PENDING_PAYMENT',paymentMethod:'VNPAY',paymentStatus:'PENDING'}),true);
});
test('cancelled COD and reconciliation are never shown as successful placement',()=>{
    const {context,nodes}=load();
    context.updateOrderStatusHeading({status:'CANCELLED',paymentMethod:'COD'});
    assert.equal(nodes.get('orderStatusTitle').textContent,'Đơn hàng đã hủy');
    context.updateOrderStatusHeading({status:'CANCELLED',paymentStatus:'RECONCILIATION_REQUIRED'});
    assert.equal(nodes.get('orderStatusTitle').textContent,'Giao dịch đang được đối soát');
});
test('order access headers use only matching order token',()=>{
    const {context,tokens}=load();tokens.set('guestOrderToken','other');
    assert.equal(context.orderAccessHeaders('A')['X-Order-Token'],undefined);
    tokens.set('beautystore:order-token:A','matching');
    assert.equal(context.orderAccessHeaders('A')['X-Order-Token'],'matching');
});
test('guest receipt still opens after member login despite legacy ownership 400',async()=>{
    const {context,tokens,nodes}=load();
    context.window.location={pathname:'/order-confirmation/ORDER-A'};
    context.localStorage.getItem=()=> 'member-token';
    tokens.set('beautystore:order-token:ORDER-A','guest-token');
    const calls=[];
    context.fetch=async(url,options)=>{
        calls.push({url,headers:options.headers});
        return url.startsWith('/api/orders/')
            ? {status:400,ok:false}
            : {status:200,ok:true,json:async()=>({orderNumber:'ORDER-A',status:'DELIVERED',paymentMethod:'COD'})};
    };
    context.displayOrderDetails=()=>{};
    context.addCancelButton=()=>{};
    context.celebrateOrder=()=>{};
    await context.loadOrderDetails();
    assert.equal(calls.length,2);
    assert.equal(calls[1].headers['X-Order-Token'],'guest-token');
    assert.equal(context.orderAccessHeaders('ORDER-A').Authorization,undefined);
    assert.equal(nodes.get('orderDetails').style.display,'block');
});
test('guest OTP resend respects cooldown, renews challenge and recovers expiry',async()=>{
    const {context,node,advance}=load(); const original=node();
    context.showGuestOtpForm(original,'ORDER','buyer@example.test',node());
    const form=original.replacement;
    const verify=form.children[2], resend=form.children[3];
    assert.equal(resend.disabled,true);
    advance(60000);assert.equal(resend.disabled,false);
    advance(240000);assert.equal(verify.disabled,true);
    let requests=0; context.fetch=async()=>{requests++;return {ok:true,json:async()=>({})};};
    await resend.events.click();
    assert.equal(requests,1);assert.equal(verify.disabled,false);assert.equal(resend.disabled,true);
});
test('order history only requires token and review bounds match backend',()=>{
    const source=fs.readFileSync(path.join(__dirname,'../../main/resources/templates/orders.html'),'utf8');
    assert.doesNotMatch(source,/!token\s*\|\|\s*!userEmail/);
    assert.match(source,/id="reviewTitle" maxlength="120"/);
    assert.match(source,/id="reviewSkinType" maxlength="50"/);
    const inline=[...source.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(match=>match[1]).join('\n');
    assert.doesNotThrow(()=>new vm.Script(inline));
});

test('receipt review submits delivered item directly without a public product lookup', async () => {
    const {context,nodes,tokens,node}=load();
    context.bootstrap={Modal:{getInstance:()=>null,getOrCreateInstance:()=>({show(){}})}};
    context.BeautyUI={uploadEvidence:async()=>[],errorMessage:async()=> 'Error'};
    tokens.set('beautystore:order-token:ORDER-A','scoped-token');
    const action=node();
    context.openOrderReview({orderNumber:'ORDER-A'},{id:5,productId:null,productName:'Archived item'},action);
    nodes.set('orderReviewStars',{value:'5'});
    let requests=0;
    context.fetch=async(url,options)=>{
        requests++;
        assert.equal(url,'/api/reviews');
        assert.equal(options.headers['X-Order-Token'],'scoped-token');
        assert.equal(JSON.parse(options.body).orderItemId,5);
        return {ok:true};
    };
    const form=nodes.get('orderReviewForm');
    await form.events.submit({preventDefault(){}});
    await form.events.submit({preventDefault(){}});
    assert.equal(requests,1);
    assert.equal(action.disabled,true);
    assert.equal(nodes.get('orderReviewSubmit').disabled,true);
});
