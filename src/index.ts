import { registerPlugin } from "@capacitor/core";

import type { UploaderPlugin } from "./definitions";

const Uploader = registerPlugin<UploaderPlugin>("Uploader", {
  web: () => import("./web").then((m) => new m.UploaderWeb()),
});

export * from "./definitions";
export { Uploader };
