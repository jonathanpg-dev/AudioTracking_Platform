// Disposable static file server for manual OAuth testing — serves this folder on http://localhost:8000
const http = require('http');
const fs = require('fs');
const path = require('path');

const root = __dirname;
const port = 8000;

const mimeTypes = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };

http.createServer((req, res) => {
  const filePath = path.join(root, req.url === '/' ? '/google-login-test.html' : req.url);
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end('Not found');
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' });
    res.end(data);
  });
}).listen(port, () => console.log(`Serving ${root} at http://localhost:${port}`));
