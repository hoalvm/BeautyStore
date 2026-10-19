// Dependency-free syntax and local asset checks, including template inline code.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'src/main/resources');
function files(directory) {
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const file = path.join(directory, entry.name);
        return entry.isDirectory() ? files(file) : [file];
    });
}
let checked = 0;
for (const file of files(path.join(resources, 'static/js'))) {
    if (!file.endsWith('.js')) continue;
    new vm.Script(fs.readFileSync(file, 'utf8'), { filename: file });
    checked++;
}
for (const file of files(path.join(resources, 'templates'))) {
    if (!file.endsWith('.html')) continue;
    const html = fs.readFileSync(file, 'utf8');
    for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
        if (/\bsrc\s*=|application\/ld\+json|application\/json/i.test(match[1])) continue;
        if (!match[2].trim()) continue;
        new vm.Script(match[2], { filename: file });
        checked++;
    }
    for (const match of html.matchAll(/th:(?:src|href)="@\{(\/(?:js|css|images|fonts)\/[^}?(]+)(?:[^}]*)\}"/g)) {
        if (!fs.existsSync(path.join(resources, 'static', match[1]))) {
            throw new Error(`Missing local asset ${match[1]} in ${path.relative(root, file)}`);
        }
    }
}
console.log(`Checked ${checked} JavaScript sources and template asset references.`);
