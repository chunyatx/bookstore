import "dotenv/config";
import { app } from "./app";
import { seedData } from "./seed";

const PORT = process.env["PORT"] ?? 3000;

seedData().then(() => {
  app.listen(PORT, () => {
    console.log(`Bookstore API running on http://localhost:${PORT}`);
    console.log(`Frontend:   http://localhost:${PORT}/`);
    console.log(`Swagger UI: http://localhost:${PORT}/docs`);
  });
});
