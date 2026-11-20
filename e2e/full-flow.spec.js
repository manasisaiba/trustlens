// Real end-to-end test: drives the TrustLens UI in Chromium through the whole journey.
// Usage: BASE_URL=http://localhost:3000 TARGET_URL=<site to audit> node e2e/full-flow.spec.js
const { chromium } = require('playwright');
const fs = require('fs');
const BASE = process.env.BASE_URL || 'http://localhost:3000';
const OUT = process.env.OUT_DIR || 'e2e/screens';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const shot = (n) => page.screenshot({ path: `${OUT}/${n}.png`, fullPage: true });
  const step = (m) => console.log(new Date().toISOString().slice(11, 19), m);

  await page.goto(BASE + '/');
  await page.getByRole('heading', { name: /Your AI customers/ }).waitFor();
  await shot('01-landing');
  await page.getByRole('link', { name: 'Test my website' }).click();
  await page.waitForURL('**/onboard');

  step('onboarding');
  // submitting empty form must show validation
  await page.getByRole('button', { name: /Create audit/ }).click();
  await page.getByText('Business name is required.').waitFor();
  await page.getByPlaceholder('Bright Dental Clinic').fill('Bright Dental Clinic');
  await page.getByPlaceholder('https://example.com').fill(process.env.TARGET_URL || `${BASE}/sample-site/index.html`);
  await page.getByRole('button', { name: 'Dental clinic' }).click();
  await page.getByPlaceholder(/What do you offer/).fill('Family dental clinic.');
  await page.getByText(/I own this website/).click();
  // wait until the server's Claude configuration has loaded
  await page.getByText(/Continue in Demo mode|Use Demo mode instead/).first().waitFor();
  const forceDemo = process.env.FORCE_DEMO === 'true';
  const demo = page.getByText(/Continue in Demo mode/);
  if (await demo.count()) await demo.click();
  else if (forceDemo) await page.getByText(/Use Demo mode instead/).click();
  await shot('02-onboarding');
  await page.getByRole('button', { name: /Create audit/ }).click();

  step('personas');
  await page.waitForURL('**/personas', { timeout: 30000 });
  await page.getByRole('button', { name: /Start browser testing/ }).waitFor({ timeout: 120000 });
  const personaCount = await page.locator('text=Task:').count();
  if (personaCount !== 4) throw new Error('expected 4 personas, got ' + personaCount);
  await shot('03-personas');
  await page.getByRole('button', { name: /Start browser testing/ }).click();

  step('live testing');
  await page.waitForURL('**/testing');
  await page.getByText('Browser tests running').waitFor({ timeout: 30000 });
  await page.waitForSelector('img[alt^="Screenshot at step"]', { timeout: 60000 });
  await page.waitForTimeout(2500);
  await shot('04-testing-live');
  await page.getByRole('button', { name: /View report/ }).waitFor({ timeout: 300000 });
  await shot('05-testing-done');

  step('report');
  await page.getByRole('button', { name: /View report/ }).click();
  await page.waitForURL('**/report');
  await page.getByText('Experience score').first().waitFor();
  await page.getByText('Browser evidence').first().waitFor();
  const findings = await page.locator('text=Simulated journey').count();
  step(`findings rendered: ${findings}`);
  await shot('06-report');
  await page.getByRole('button', { name: 'View screenshot' }).first().click();
  await page.getByAltText('Evidence screenshot').waitFor();
  await shot('07-evidence-lightbox');
  await page.keyboard.press('Escape');
  await page.mouse.click(10, 10);

  step('preview');
  await page.getByRole('button', { name: /improved preview/i }).click();
  await page.waitForURL('**/preview');
  await page.frameLocator('iframe[title="Improved preview"]').locator('h1').waitFor({ timeout: 180000 });
  await page.waitForTimeout(1500);
  await shot('08-preview');
  // interact inside the sandboxed preview
  const frame = page.frameLocator('iframe[title="Improved preview"]');
  await frame.locator('a[href="#contact"]').first().click();
  await frame.locator('input').first().fill('Test Customer');
  await page.getByRole('button', { name: 'mobile' }).click();
  await page.waitForTimeout(800);
  await shot('09-preview-mobile');

  step('approval');
  await page.getByRole('link', { name: /Review & approve/ }).click();
  await page.waitForURL('**/approval');
  const approveBtns = page.getByRole('button', { name: /^Approve$/ });
  await approveBtns.first().click();
  await page.getByRole('button', { name: /^Approved$/ }).first().waitFor();
  await page.getByRole('button', { name: /^Reject$/ }).nth(1).click();
  await page.getByRole('button', { name: /^Rejected$/ }).first().waitFor();
  await page.getByPlaceholder(/Notes for your web team/).fill('Looks good, verify phone first.');
  await page.locator('div.grid > button', { hasText: 'Approve' }).last().click();
  await page.getByText('This preview is approved').waitFor();
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('link', { name: /Export HTML/ }).click()]);
  const html = fs.readFileSync(await download.path(), 'utf8');
  if (!html.includes('<!doctype html>') || html.includes('<script')) throw new Error('bad export');
  await shot('10-approval');

  await page.goto(BASE + '/audits');
  await shot('11-audits-list');
  if (errors.length) throw new Error('Page errors: ' + errors.join(' | '));
  step('E2E PASSED');
  await browser.close();
})().catch((e) => { console.error('E2E FAILED:', e.message); process.exit(1); });
