import { mkdir, copyFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { build, context } from 'esbuild';

const watch = process.argv.includes('--watch');
const output = 'target/generated-resources/static';
await Promise.all(['css', 'js'].map(dir => mkdir(`${output}/${dir}`, { recursive: true })));
await copyFile('node_modules/quill/dist/quill.snow.css', `${output}/css/editor.css`);
const options = {
  entryPoints: {
    kairos: 'src/main/frontend/app.js',
    theme: 'src/main/frontend/theme.js',
    editor: 'src/main/frontend/editor.js'
  },
  bundle: true,
  outdir: `${output}/js`,
  format: 'iife',
  target: 'es2020',
  minify: !watch,
  sourcemap: watch,
  legalComments: 'linked'
};
if (watch) await (await context(options)).watch();
else await build(options);
const css = spawn(process.execPath, [
  'node_modules/@tailwindcss/cli/dist/index.mjs',
  '-i', 'src/main/frontend/styles.css', '-o', `${output}/css/kairos.css`,
  watch ? '--watch' : '--minify'
], { stdio: 'inherit' });
css.on('exit', code => { if (code) process.exitCode = code; });
