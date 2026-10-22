// Release packaging replaces these values. Production validation rejects an
// empty/non-HTTPS business origin. Legacy access is explicit and read-only by
// default so an unrelated token can never be reused accidentally.
module.exports = {
  environment: 'staging',
  businessBaseUrl: 'https://qinglife.guijianju.cn/accept-20261001-api',
  allowedBusinessOrigins: [],
  legacyEnabled: false,
  legacyBaseUrl: '',
  legacyWritesEnabled: false
}
