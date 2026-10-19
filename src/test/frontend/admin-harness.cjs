const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function element() {
    const classes = new Set();
    return {
        value: '', checked: false, disabled: false, textContent: '', dataset: {}, style: {}, children: [],
        options: [], selectedOptions: [], files: [], classList: {
            add(name) { classes.add(name); }, remove(name) { classes.delete(name); }, contains(name) { return classes.has(name); },
            toggle(name, value) { if (value ?? !classes.has(name)) classes.add(name); else classes.delete(name); }
        },
        setAttribute(key, value) { this[key] = value; }, removeAttribute(key) { delete this[key]; },
        append(...nodes) { this.children.push(...nodes); }, appendChild(node) { this.children.push(node); },
        prepend(node) { this.children.unshift(node); }, replaceChildren(...nodes) { this.children = nodes; },
        addEventListener() {}, querySelectorAll() { return []; }, reportValidity() { return true; }, focus() {}, remove() {}
    };
}

function load(name, overrides = {}) {
    const nodes = new Map();
    const document = {
        addEventListener() {}, querySelectorAll() { return []; }, querySelector() { return element(); },
        getElementById(id) { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
        createElement: element, createDocumentFragment: element, createTextNode: text => ({textContent: text}), body: element()
    };
    const location = {origin: 'https://beauty.test', pathname: '/', search: ''};
    const context = vm.createContext({document, location, window: {location, alert() {}, addEventListener() {}}, console,
        localStorage: {getItem() { return 'test-token'; }}, URL, URLSearchParams, Intl, Date, Set, Map,
        setTimeout() {}, clearTimeout() {}, confirm: () => true, prompt: () => 'Ghi chú',
        fetch: async () => { throw new Error('Unexpected request'); }, ...overrides});
    vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', name), 'utf8'), context);
    return {context, nodes, document, run: source => vm.runInContext(source, context)};
}

module.exports = {load, element};
