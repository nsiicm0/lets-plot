import asyncio
from playwright.async_api import async_playwright
import os

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()
        path = os.path.abspath('demo_ggdeck.html')
        await page.goto(f"file://{path}")
        await page.wait_for_selector('svg')
        await page.wait_for_timeout(2000) # give it time to render
        svgs = await page.evaluate('''() => {
            return Array.from(document.querySelectorAll("svg")).map(svg => svg.outerHTML);
        }''')
        
        with open("svg_dump.txt", "w") as f:
            for i, svg in enumerate(svgs):
                f.write(f"--- SVG {i} ---\\n{svg}\\n")
        
        print("Done. Saved to svg_dump.txt")
        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())
