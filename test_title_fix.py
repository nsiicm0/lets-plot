import asyncio
from playwright.async_api import async_playwright
import os

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()
        url = f"file://{os.path.abspath('demo_ggdeck.html')}"
        await page.goto(url)
        await page.wait_for_timeout(2000)
        
        await page.evaluate('''() => {
            const leftGroups = document.querySelectorAll('g.axis-left');
            leftGroups.forEach((g) => {
                // Find the associated title text
                const p = g.parentElement;
                const titleText = p.querySelector('text.axis-title-y');
                if (titleText) {
                    // Compute spine height center
                    let spineMin = 10000, spineMax = -10000;
                    g.querySelectorAll('line').forEach(l => {
                        if (l.getAttribute('x1') === '0' && l.getAttribute('x2') === '0') {
                            const y1 = parseFloat(l.getAttribute('y1') || '0');
                            const y2 = parseFloat(l.getAttribute('y2') || '0');
                            spineMin = Math.min(spineMin, y1, y2);
                            spineMax = Math.max(spineMax, y1, y2);
                        }
                    });
                    const yMiddle = (spineMin + spineMax) / 2;
                    
                    // Box is x="-58" width="62" -> right edge is 4. Center of left margin is roughly -45
                    titleText.setAttribute("transform", `translate(-45, ${yMiddle}) rotate(-90)`);
                    titleText.style.textAnchor = "middle";
                    g.appendChild(titleText);
                }
            });
            
            const rightGroups = document.querySelectorAll('g.axis-right');
            rightGroups.forEach((g) => {
                const p = g.parentElement;
                const titleText = p.querySelector('text.axis-title-y');
                if (titleText) {
                    let spineMin = 10000, spineMax = -10000;
                    g.querySelectorAll('line').forEach(l => {
                        if (l.getAttribute('x1') === '0' && l.getAttribute('x2') === '0') {
                            const y1 = parseFloat(l.getAttribute('y1') || '0');
                            const y2 = parseFloat(l.getAttribute('y2') || '0');
                            spineMin = Math.min(spineMin, y1, y2);
                            spineMax = Math.max(spineMax, y1, y2);
                        }
                    });
                    const yMiddle = (spineMin + spineMax) / 2;
                    
                    // Box is x="-4" width="62" -> right edge is 58. Center of right margin is roughly 45
                    titleText.setAttribute("transform", `translate(45, ${yMiddle}) rotate(-90)`);
                    titleText.style.textAnchor = "middle";
                    g.appendChild(titleText);
                }
            });
        }''')
        
        await page.screenshot(path="demo_ggdeck_test_fix.png", full_page=True)
        await browser.close()

if __name__ == '__main__':
    asyncio.run(main())
