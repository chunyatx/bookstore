// CJS shim for uuid used only in the Jest (CommonJS) test environment.
// uuid v13 is ESM-only; Node.js crypto.randomUUID() is a drop-in replacement.
const { randomUUID } = require("crypto");
module.exports = { v4: randomUUID };
