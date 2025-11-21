import {defineConfig} from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";


const host = process.env.TAURI_DEV_HOST;

export default defineConfig({
    plugins: [
        scalaJSPlugin({
            // path to the directory containing the sbt build
            // default: '.'
            cwd: '../../',

            // sbt project ID from within the sbt build to get fast/fullLinkJS from
            // default: the root project of the sbt build
            projectID: 'tausi-sample',
        })
    ],
    clearScreen: false,
    server: {
        port: 1420,
        strictPort: true,
        host: host || false,
        hmr: host
            ? {
                protocol: "ws",
                host,
                port: 1421,
            }
            : undefined,
        watch: {
            ignored: ["**/src-tauri/**", "**/src/main/**", "**/src/test/**"],
        },
    }
});
