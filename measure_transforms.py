import asyncio
from playwright.async_api import async_playwright
import os
import json

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()
        url = f"file://{os.path.abspath('demo_ggdeck.html')}"
        await page.goto(url)
        await page.wait_for_timeout(2000)
        
        transforms = await page.evaluate('''() => {
            const groups = Array.from(document.querySelectorAll('g.axis-left, g.axis-right'));
            return groups.map(g => {
                const titleText = g.parentElement.querySelector('.axis-title-y'); // wait, the title group is a sibling of axis group usually? 
                // Let's just find the text elements.
                return null;
            });
        }''')
        
        data = await page.evaluate('''() => {
            const texts = Array.from(document.querySelectorAll('text')).filter(t => t.textContent.trim() === 'y');
            return texts.map(t => ({
                textTransform: t.getAttribute('transform'),
                parentTransform: t.parentElement.getAttribute('transform'),
                parentTag: t.parentElement.tagName,
                bBox: t.getBoundingClientRect().toJSON()
            }));
        }''')
        
        print(json.dumps(data, indent=2))
        await browser.close()

if __name__ == '__main__':
    asyncio.run(main())
