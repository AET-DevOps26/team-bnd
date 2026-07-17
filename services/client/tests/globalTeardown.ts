import { execSync } from "child_process";
import fs from "fs";
import path from "path";

/**
 * Runs once after the whole e2e suite. The instrumented client is built in the
 * container at /app, so the istanbul coverage the fixture collected points
 * there; rewrite those paths to the local client dir, then produce the nyc
 * report (coverage-e2e/, per .nycrc.json). No-op when the client wasn't
 * instrumented (nothing was collected).
 */
export default function globalTeardown() {
  const clientDir = path.resolve(__dirname, "..");
  const nycOutput = path.join(clientDir, ".nyc_output");
  if (!fs.existsSync(nycOutput)) return;

  const root = clientDir + "/";
  for (const file of fs.readdirSync(nycOutput)) {
    if (!file.endsWith(".json")) continue;
    const p = path.join(nycOutput, file);
    fs.writeFileSync(p, fs.readFileSync(p, "utf8").split("/app/").join(root));
  }

  try {
    execSync("npx nyc report", { cwd: clientDir, stdio: "inherit" });
  } catch (err) {
    console.error("[globalTeardown] nyc report failed:", err);
  }
}
