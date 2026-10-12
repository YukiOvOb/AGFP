import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function prose(text) {
  return text.replace(/^(`{3,}|~{3,})[^\n]*\n[\s\S]*?^\1[^\n]*$/gm, '')
    .replace(/<!--[\s\S]*?-->/g, '');
}

function headingSlug(text) {
  return text.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/[`*_]/g, '').trim().toLowerCase()
    .replace(/[^\p{L}\p{N}\p{M}_\- ]/gu, '').replace(/ /g, '-');
}

async function exactPath(root, target) {
  const relative = path.relative(root, target);
  if (relative.startsWith(`..${path.sep}`) || relative === '..' || path.isAbsolute(relative)) return false;
  let current = root;
  for (const component of relative.split(path.sep).filter(Boolean)) {
    let entries;
    try { entries = await readdir(current); } catch { return false; }
    if (!entries.includes(component)) return false;
    current = path.join(current, component);
  }
  return true;
}

async function anchors(filename) {
  const text = prose(await readFile(filename, 'utf8'));
  const result = new Set();
  const counts = new Map();
  for (const match of text.matchAll(/^#{1,6}\s+(.+?)\s*#*\s*$/gm)) {
    const slug = headingSlug(match[1]);
    const count = counts.get(slug) ?? 0;
    result.add(count ? `${slug}-${count}` : slug);
    counts.set(slug, count + 1);
  }
  return result;
}

async function skills(root) {
  const folder = path.join(root, '.agents', 'skills');
  return (await readdir(folder, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory() && entry.name.startsWith('serms-'))
    .map((entry) => `.agents/skills/${entry.name}/SKILL.md`);
}

export async function check(root = defaultRoot) {
  root = path.resolve(root);
  const skillPaths = await skills(root);
  const documents = [
    'AGENTS.md', 'README.md', '.github/CONTRIBUTING.md', '.github/pull_request_template.md',
    '.claude/CLAUDE.md', 'docs/README.md', 'docs/contracts/README.md', 'docs/testing.md',
    'docs/agile/sprint-plan.md', 'docs/collaboration/ownership.md',
    'docs/collaboration/agent-workflow.md', 'docs/collaboration/decisions.md', ...skillPaths,
  ];
  const problems = [];
  const anchorCache = new Map();
  for (const document of documents) {
    const filename = path.join(root, document);
    let raw;
    try { raw = await readFile(filename, 'utf8'); } catch {
      problems.push(`${document}: missing document`);
      continue;
    }
    if (raw.charCodeAt(0) === 0xfeff || raw.includes('\r')) {
      problems.push(`${document}: use UTF-8 without BOM and LF`);
    }
    const text = prose(raw);
    for (const match of text.matchAll(/\[[^\]]*\]\(([^\s)]+)\)/g)) {
      const link = match[1];
      if (/^[a-z][a-z\d+.-]*:/i.test(link)) continue;
      if (link.includes('\\')) {
        problems.push(`${document}: ${link} (use forward slashes)`);
        continue;
      }
      let targetText;
      try { targetText = decodeURIComponent(link); } catch {
        problems.push(`${document}: invalid escaped URL ${link}`);
        continue;
      }
      const [file, fragment] = targetText.split('#');
      const target = file
        ? path.resolve(path.dirname(filename), file.split('?')[0]) : filename;
      if (!(await exactPath(root, target))) {
        problems.push(`${document}: ${link} (missing, case mismatch, or outside repository)`);
        continue;
      }
      if (fragment && target.endsWith('.md') && (await stat(target)).isFile()) {
        if (!anchorCache.has(target)) anchorCache.set(target, await anchors(target));
        if (!anchorCache.get(target).has(fragment)) {
          problems.push(`${document}: ${link} (missing heading)`);
        }
      }
    }
    if (document.includes('/skills/')) {
      const name = raw.match(/^name:\s*(\S+)\s*$/m)?.[1];
      const description = raw.match(/^description:\s*(.+)$/m)?.[1];
      if (!raw.startsWith('---\n') || name !== path.basename(path.dirname(filename)) || !description) {
        problems.push(`${document}: invalid skill name or description`);
      }
      if (!text.includes('来源：')) problems.push(`${document}: missing rule source links`);
    }
  }
  const entry = await readFile(path.join(root, 'AGENTS.md'), 'utf8');
  for (const skill of skillPaths) {
    if (!entry.includes(`](${skill})`)) problems.push(`${skill}: absent from AGENTS routing`);
  }
  if (!skillPaths.length) problems.push('No SERMS skills found');
  const claude = await readFile(path.join(root, '.claude/CLAUDE.md'), 'utf8');
  if (claude.split('\n')[0] !== '@../AGENTS.md') {
    problems.push('.claude/CLAUDE.md: expected shared AGENTS import');
  }
  return { documents: documents.length, skills: skillPaths.length, problems };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = await check();
    if (result.problems.length) {
      for (const problem of result.problems) console.error(problem);
      process.exitCode = 1;
    } else {
      console.log(`Collaboration documentation passed: ${result.documents} documents, ${result.skills} skills.`);
    }
  } catch (error) {
    console.error(`Collaboration documentation failed: ${error.message}`);
    process.exitCode = 1;
  }
}
