/* eslint-disable @typescript-eslint/no-require-imports */
const path = require("path");
const fs = require("fs");
const { execSync } = require("child_process");
const esbuild = require("esbuild");

const projectRoot = path.resolve(__dirname, "..");
const buildDir = path.join(projectRoot, "plugin-build");
const resourcesDir = path.resolve(projectRoot, "..", "backend", "src", "main", "resources", "frontend");
const publicDir = path.join(projectRoot, "public");
const staticAssets = [
  { source: "SAS_Black_Logo.png", type: "file" },
];

function ensureDir(dir) {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

function runTailwind() {
  console.log("[build-plugin] Generating CSS with tailwindcss...");
  ensureDir(buildDir);
  const tailwindCommand =
    "npx tailwindcss -i ./app/globals.css -o ./plugin-build/rail-portal.css --config ./tailwind.config.ts --minify";
  console.log(`[build-plugin] Running: ${tailwindCommand}`);
  execSync(tailwindCommand, {
    cwd: projectRoot,
    stdio: "inherit",
  });
  const cssPath = path.join(buildDir, "rail-portal.css");
  const cssSize = fs.existsSync(cssPath) ? fs.statSync(cssPath).size : 0;
  console.log(`[build-plugin] Generated CSS size: ${cssSize} bytes`);
}

async function runEsbuild() {
  console.log("[build-plugin] Bundling React app with esbuild...");
  await esbuild.build({
    entryPoints: [path.join(projectRoot, "plugin-entry.tsx")],
    outfile: path.join(buildDir, "rail-portal.js"),
    bundle: true,
    format: "iife",
    platform: "browser",
    sourcemap: false,
    minify: true,
    target: ["es2018"],
    tsconfig: path.join(projectRoot, "tsconfig.json"),
    jsx: "automatic",
    jsxImportSource: "react",
    treeShaking: true,
    splitting: false,
    banner: {
      js: '/* RAIL Portal Plugin Bundle */ if (typeof process === "undefined") { var process = { env: { NODE_ENV: "production" } }; } if (typeof global === "undefined") { var global = globalThis; }'
    },
    define: {
      "process.env.NODE_ENV": '"production"',
    },
    external: [],
    inject: [],
    loader: {
      '.png': 'dataurl',
      '.svg': 'dataurl',
      '.jpg': 'dataurl',
      '.jpeg': 'dataurl',
      '.woff': 'dataurl',
      '.woff2': 'dataurl',
      '.ttf': 'dataurl',
      '.eot': 'dataurl'
    }
  });
  const cssPath = path.join(buildDir, "rail-portal.css");
  if (fs.existsSync(cssPath)) {
    console.log(
      `[build-plugin] CSS size after esbuild: ${fs.statSync(cssPath).size} bytes`,
    );
  }
}

function copyArtifacts() {
  ensureDir(resourcesDir);
  const artifacts = ["rail-portal.css", "rail-portal.js"];
  for (const file of artifacts) {
    const source = path.join(buildDir, file);
    const target = path.join(resourcesDir, file);
    fs.copyFileSync(source, target);
    console.log(`[build-plugin] Copied ${file} -> ${path.relative(projectRoot, target)}`);
    if (file === "rail-portal.css") {
      console.log(
        `[build-plugin] CSS size after copy: ${fs.statSync(source).size} bytes`,
      );
    }
  }
}

function copyStaticAssets() {
  if (!fs.existsSync(publicDir)) {
    return;
  }

  ensureDir(resourcesDir);
  for (const asset of staticAssets) {
    const sourcePath = path.join(publicDir, asset.source);
    const targetPath = path.join(resourcesDir, asset.source);

    if (!fs.existsSync(sourcePath)) {
      console.warn(
        `[build-plugin] Static asset ${asset.source} not found in public/, skipping.`,
      );
      continue;
    }

    if (asset.type === "dir" && fs.lstatSync(sourcePath).isDirectory()) {
      copyDirectoryRecursive(sourcePath, targetPath);
    } else {
      ensureDir(path.dirname(targetPath));
      fs.copyFileSync(sourcePath, targetPath);
      console.log(
        `[build-plugin] Copied asset public/${asset.source} -> ${path.relative(projectRoot, targetPath)}`,
      );
    }
  }
}
function copyDirectoryRecursive(source, destination) {
  ensureDir(destination);
  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const sourcePath = path.join(source, entry.name);
    const targetPath = path.join(destination, entry.name);
    if (entry.isDirectory()) {
      copyDirectoryRecursive(sourcePath, targetPath);
    } else {
      ensureDir(path.dirname(targetPath));
      fs.copyFileSync(sourcePath, targetPath);
    }
  }
  console.log(`[build-plugin] Copied directory ${path.relative(publicDir, source)} -> ${path.relative(projectRoot, destination)}`);
}

(async function build() {
  try {
    await runEsbuild();
    runTailwind();
    copyArtifacts();
    copyStaticAssets();
    console.log("[build-plugin] Build completed successfully.");
  } catch (error) {
    console.error("[build-plugin] Build failed:", error);
    process.exitCode = 1;
  }
})();
