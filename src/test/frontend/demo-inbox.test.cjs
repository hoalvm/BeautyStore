const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

async function load() {
    const node = () => ({disabled: false, textContent: '', children: [], events: {},
        addEventListener(type, callback) {this.events[type] = callback;},
        append(child) {this.children.push(child);}, replaceChildren() {this.children = [];},
        removeAttribute(name) {delete this[name];}});
    const nodes = Object.fromEntries(['refreshMail','mailList','mailStatus','mailPreview'].map(id=>[id,node()]));
    const pending = [];
    const context = {
        document: {getElementById: id=>nodes[id],createElement:node},
        fetch: url => url === '/demo/api/mail'
            ? Promise.resolve({ok:true,json:async()=>[{id:'first',subject:'First',recipient:'demo@example.test'},
                {id:'second',subject:'Second',recipient:'demo@example.test'}]})
            : new Promise(resolve=>pending.push({url,resolve}))
    };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/demo-inbox.js'),'utf8'),context);
    await new Promise(resolve=>setImmediate(resolve));
    return {nodes,pending};
}

test('demo email uses sandboxed srcdoc with CSP, not a frame-denied navigation',async()=>{
    const {nodes,pending}=await load();
    const request=nodes.mailList.children[0].children[0].events.click();
    pending[0].resolve({ok:true,text:async()=>'<html><body>Demo mail</body></html>'});
    await request;
    assert.equal(nodes.mailPreview.src,undefined);
    assert.match(nodes.mailPreview.srcdoc,/Content-Security-Policy/);
    assert.match(nodes.mailPreview.srcdoc,/default-src 'none'/);
    assert.match(nodes.mailPreview.srcdoc,/Demo mail/);
    const template=fs.readFileSync(path.join(__dirname,'../../main/resources/templates/demo/inbox.html'),'utf8');
    assert.match(template,/id="mailPreview"[^>]*sandbox=""/);
});

test('demo inbox discards stale selection and reports expired mail',async()=>{
    const {nodes,pending}=await load();
    const first=nodes.mailList.children[0].children[0].events.click();
    const second=nodes.mailList.children[1].children[0].events.click();
    pending[1].resolve({ok:true,text:async()=>'<p>Second mail</p>'});await second;
    pending[0].resolve({ok:true,text:async()=>'<p>First mail</p>'});await first;
    assert.match(nodes.mailPreview.srcdoc,/Second mail/);
    assert.doesNotMatch(nodes.mailPreview.srcdoc,/First mail/);
    const expired=nodes.mailList.children[0].children[0].events.click();
    pending[2].resolve({ok:false});await expired;
    assert.match(nodes.mailStatus.textContent,/hết hạn/);
    assert.equal(nodes.mailPreview.srcdoc,'');
});
