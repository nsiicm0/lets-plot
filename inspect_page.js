const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  
  page.on('console', msg => {
    if (msg.type() === 'error') console.log(`[Error] ${msg.text()}`);
  });
  
  await page.goto(`file://${path.resolve('demo_ggdeck.html')}`);
  await page.waitForTimeout(2000);
  
  const transforms = await page.evaluate(() => {
    const els = document.querySelectorAll('*');
    const res = [];
    for (const el of els) {
      if (el.getAttribute && el.getAttribute('transform')?.includes('--')) {
        res.push({
          tag: el.tagName,
          classes: el.getAttribute('class'),
          transform: el.getAttribute('transform')
        });
      }
    }
    return res;
  });
  
  console.log("Nodes with '--':", transforms);
  await browser.close();
})();
