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
        
        data = await page.evaluate('''() => {
            const leftGroups = document.querySelectorAll('g.axis-left');
            const rightGroups = document.querySelectorAll('g.axis-right');
            const res = [];
            
            leftGroups.forEach((g, i) => {
                const rect = g.querySelector('rect');
                const title = g.querySelector('.axis-title-y') || g.parentElement.querySelector('.axis-title-y'); // wait, the title might be moved
                
                // Let's just find rects and titles directly globally to be safe
                return;
            });
            
            const allRects = Array.from(document.querySelectorAll('g.axis-left rect, g.axis-right rect'));
            const output = {};
            
            allRects.forEach((r, i) => {
                const g = r.parentElement;
                const texts = Array.from(g.querySelectorAll('text'));
                const bBox = r.getBoundingClientRect();
                
                // find the title text (usually just "y" with class or rotated)
                const titleText = texts.find(t => t.textContent.trim() === 'y' || t.classList.contains('axis-title-y')) 
                                  || document.evaluate("//text[contains(., 'y')]", document, null, XPathResult.ANY_TYPE, null).iterateNext();
                
                // actually let's just grab ALL text elements named "y" and ALL rects.
            });
            
            const rects = Array.from(document.querySelectorAll('rect')).filter(r => r.getAttribute('width') === '62').map(r => ({
                color: r.getAttribute('stroke'),
                rect: r.getBoundingClientRect().toJSON()
            }));
            
            const titles = Array.from(document.querySelectorAll('text')).filter(t => t.textContent.trim() === 'y').map(t => ({
                color: t.style.fill || t.getAttribute('fill'),
                rect: t.getBoundingClientRect().toJSON(),
                transform: t.getAttribute('transform')
            }));
            
            return { rects, titles };
        }''')
        
        print(json.dumps(data, indent=2))
        await browser.close()

if __name__ == '__main__':
    asyncio.run(main())
