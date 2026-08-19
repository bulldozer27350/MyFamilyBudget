const fs = require('fs');
const path = require('path');
const vm = require('vm');

async function test() {
  console.log("Fetching Babel...");
  const res = await fetch('https://unpkg.com/@babel/standalone/babel.min.js');
  const babelCode = await res.text();
  
  const sandbox = { window: {}, console };
  vm.createContext(sandbox);
  vm.runInContext(babelCode, sandbox);
  const Babel = sandbox.Babel || sandbox.window.Babel;
  
  console.log("Babel loaded successfully:", !!Babel);
  
  const sampleCode = `
    function MyComponent({ title }) {
      return <div className="card"><h1>{title}</h1></div>;
    }
  `;
  
  const transformed = Babel.transform(sampleCode, {
    presets: [['react', { runtime: 'classic' }]]
  }).code;
  
  console.log("Transformed code:\n", transformed);
}

test().catch(console.error);
