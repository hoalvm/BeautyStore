const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function element(id = '') {
    const classes = new Set();
    return {
        id, value: '', type: 'password', textContent: '', className: '', disabled: false,
        dataset: {}, attributes: {}, children: [], listeners: {},
        classList: {
            toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); },
            add(name) { classes.add(name); }, remove(name) { classes.delete(name); },
            contains(name) { return classes.has(name); }
        },
        setAttribute(name, value) { this.attributes[name] = value; },
        addEventListener(name, callback) { (this.listeners[name] ||= []).push(callback); },
        async fire(name) {
            await Promise.all((this.listeners[name] || []).map(callback => callback({
                currentTarget: this, target: this, preventDefault() {}
            })));
        },
        reportValidity() { return true; },
        querySelector(selector) { return selector === 'i' ? (this.icon ||= element()) : (this.submit ||= element()); },
        querySelectorAll() { return []; },
        append(...children) { this.children.push(...children); },
        replaceChildren(...children) { this.children = children; },
        reset() { this.wasReset = true; }, focus() { this.focused = true; }
    };
}

function storage(initial = {}) {
    const map = new Map(Object.entries(initial));
    return {getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, String(value)), removeItem: key => map.delete(key)};
}

function harness(ids, options = {}) {
    const nodes = Object.fromEntries(ids.map(id => [id, element(id)]));
    const ready = [];
    const calls = [];
    const localStorage = storage(options.local);
    const sessionStorage = storage(options.session);
    const window = {
        location: {href: '', search: options.search || '', origin: 'https://beauty.example'}, addEventListener() {},
        setInterval() { return 1; }, clearInterval() {},
        setTimeout() { return 1; }
    };
    const context = vm.createContext({
        window, localStorage, sessionStorage, URL, URLSearchParams, TypeError, console,
        document: {
            querySelector() { return null; }, querySelectorAll() { return []; },
            getElementById: id => nodes[id] || null,
            createElement: () => element(),
            addEventListener(name, handler) { if (name === 'DOMContentLoaded') ready.push(handler); }
        },
        fetch(url, config) {
            calls.push({url, config});
            return options.fetch ? options.fetch(url, config) : Promise.resolve(response({}));
        },
        bootstrap: {Modal: {getOrCreateInstance: () => ({show() {}})}}
    });
    function load(file) {
        const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', file), 'utf8');
        vm.runInContext(script, context, {filename: file});
    }
    load('auth.js');
    if (options.profile) load('profile.js');
    return {nodes, window, calls, localStorage, sessionStorage,
        async ready() { ready.forEach(handler => handler()); await settle(); }};
}

const response = (data, status = 200) => ({ok: status >= 200 && status < 300, status, json: async () => data});
const settle = () => new Promise(resolve => setImmediate(resolve));
module.exports = {harness, response, settle, element};
