import { readdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
const jars = (await readdir('target')).filter(name => /^kairos-.*\.jar$/.test(name));
if (jars.length !== 1) throw new Error('Run mvn clean package before browser tests (expected one Kairos JAR).');
const child = spawn('java', ['-jar', `target/${jars[0]}`, '--server.port=18080', '--spring.datasource.url=jdbc:h2:mem:ui', '--spring.jpa.hibernate.ddl-auto=update'], { stdio:'inherit' });
for (const signal of ['SIGINT','SIGTERM']) process.on(signal, () => child.kill(signal));
child.on('exit', code => { process.exitCode = code ?? 1; });
