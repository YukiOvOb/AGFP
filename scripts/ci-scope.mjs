import {appendFileSync, readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';

export function needsApplicationChecks(files) {
  // Only prose/evidence and Git ignore rules bypass application work.
  // Unknown paths, workflow changes and mixed changes always run the full suite.
  return files.length === 0 || files.some(file =>
    !(file.endsWith('.md') || file.startsWith('docs/') || file === '.gitignore'));
}

export function determineScope(env = process.env, git = args =>
  execFileSync('git', args, {encoding:'utf8', timeout:10000, stdio:['ignore','pipe','pipe']})) {
  try {
    const head = git(['rev-parse', 'HEAD']).trim();
    if (head !== env.GITHUB_SHA) throw new Error('checkout mismatch');
    const event = JSON.parse(readFileSync(env.GITHUB_EVENT_PATH, 'utf8'));
    let base;
    if (env.GITHUB_EVENT_NAME === 'pull_request') {
      // Compare the actual tested merge with its target parent; fetch-depth: 2 is sufficient.
      git(['rev-parse', '--verify', 'HEAD^2']);
      base = git(['rev-parse', 'HEAD^1']).trim();
    } else if (env.GITHUB_EVENT_NAME === 'push') {
      base = event.before;
    } else {
      throw new Error('unsupported event');
    }
    if (!/^[a-f0-9]{40}$/.test(base ?? '') || /^0+$/.test(base))
      throw new Error('missing baseline');
    const files = git(['diff', '--name-only', '--no-renames', '-z', base, head, '--'])
      .split('\0').filter(Boolean);
    return {run:needsApplicationChecks(files), reason:needsApplicationChecks(files)
      ? 'Application, automation or unknown changes: full checks required'
      : 'Only documentation/evidence/Git ignore changes: application checks are unchanged'};
  } catch {
    // No API requests or extra Git fetches. A shallow/missing baseline never suppresses tests.
    return {run:true, reason:'Scope could not be proved from the local checkout: full checks required'};
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const scope = determineScope();
  console.log(scope.reason);
  appendFileSync(process.env.GITHUB_OUTPUT, `run=${scope.run}\n`);
}
