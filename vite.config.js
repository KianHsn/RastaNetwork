import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

function classicScript() {
  return {
    name: "classic-script",
    closeBundle() {
      const file = resolve(import.meta.dirname, "dist/index.html");
      const html = readFileSync(file, "utf8")
        .replaceAll('<script type="module" crossorigin>', "<script>")
        .replaceAll('<script type="module">', "<script>");
      writeFileSync(file, html);
    },
  };
}

export default defineConfig({
  root: "web",
  plugins: [viteSingleFile(), classicScript()],
  build: {
    outDir: "../dist",
    emptyOutDir: true,
    cssCodeSplit: false,
    assetsInlineLimit: 100000000,
    modulePreload: false,
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
});
