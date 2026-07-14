// SSM parameter that ApiStack publishes MainDraftHandler's WebSocket callback URL to, and that
// LambdaStack grants MainDraftHandler read access to. The name is a hardcoded convention shared by
// both stacks deliberately: neither stack needs a CDK cross-stack reference to agree on it, which
// avoids a circular dependency (ApiStack already depends on LambdaStack for the Lambda alias).
export const WEBSOCKET_CALLBACK_URL_PARAM_NAME = '/monstercubedraft/dev/websocket-callback-url';
