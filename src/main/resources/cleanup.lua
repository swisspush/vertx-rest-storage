local resourcesPrefix = ARGV[1]
local collectionsPrefix = ARGV[2]
local deltaResourcesPrefix = ARGV[3]
local deltaEtagsPrefix = ARGV[4]
local expirableSet = ARGV[5]
local minscore = tonumber(ARGV[6])
local maxscore = tonumber(ARGV[7])
local confirmCollectionDelete = ARGV[8]
local deleteRecursive = ARGV[9]
local now = tonumber(ARGV[10])
local bulksize = tonumber(ARGV[11])
local delScriptSha = ARGV[12]

local resourcePrefixLength = string.len(resourcesPrefix)
local counter = 0
local KEYS = {}
local resourcesToClean = redis.call('zrangebyscore',expirableSet,minscore,now,'limit',0,bulksize)
for key,value in pairs(resourcesToClean) do
  redis.log(redis.LOG_NOTICE, "cleanup resource: "..value)
  KEYS[1] = string.sub(value, resourcePrefixLength+1, string.len(value))
  
  --%(delscript)
  
  counter = counter + 1
end
return counter