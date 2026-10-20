# Vendored browser dependencies

These runtime assets are committed so that storefront, admin and shipper pages
work without reaching a CDN after the application starts. No npm install is needed
to run the Java application.

Exact versions already used by the templates were retained:

| Package | Version | Files |
| --- | --- | --- |
| Bootstrap | 5.3.0 | compiled CSS, bundled JS (includes Popper), maps, MIT license |
| Font Awesome Free | 6.0.0 | storefront CSS, webfonts, upstream license |
| Font Awesome Free | 6.4.0 | admin/shipper CSS, webfonts, upstream license |
| Chart.js | 4.4.0 | UMD bundle, map, MIT license |

Source: the publishers' npm packages, downloaded from
`https://registry.npmjs.org/{package}/{version}`. Archive SHA-512 digests were
checked against `dist.integrity` before extracting only the distributed browser
assets and license files. No package install scripts were executed.

Upstream installation instructions:

- https://getbootstrap.com/docs/5.3/getting-started/download/
- https://docs.fontawesome.com/web/setup/packages
- https://www.chartjs.org/docs/latest/getting-started/installation.html

Keep the upstream licenses alongside each package when updating these files.
Chart.js has no `chart.min.css`; the previous nonexistent stylesheet references
have been removed.
