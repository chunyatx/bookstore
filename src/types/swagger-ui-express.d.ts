// Stub type declaration for swagger-ui-express (package not installed in this environment)
declare module "swagger-ui-express" {
  import { RequestHandler } from "express";
  const serve: RequestHandler[];
  function setup(spec: object, options?: object): RequestHandler;
  export { serve, setup };
}
