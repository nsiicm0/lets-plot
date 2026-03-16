import asyncio
from playwright.async_api import async_playwright
import os

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()
        
        page.on("console", lambda msg: print(f"[Console] {msg.text}") if msg.type == "error" else None)
        
        url = f"file://{os.path.abspath('demo_ggdeck.html')}"
        await page.goto(url)
        await page.wait_for_timeout(2000)
        
        transforms = await page.evaluate('''() => {
            const els = document.querySelectorAll('*');
            const res = [];
            for (const el of els) {
                const t = el.getAttribute('transform');
                if (t && t.includes('--')) {
                    res.push({
                        tag: el.tagName,
                        classes: el.getAttribute('class'),
                        transform: t
                    });
                }
            }
            return res;
        }''')
        
        print("Nodes with '--':", transforms)
        await browser.close()

if __name__ == '__main__':
    asyncio.run(main())
