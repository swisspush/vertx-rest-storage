-- --------------------------------------------------------------------------------------------
-- Copyright 2014 by Swiss Post, Information Technology Services
-- --------------------------------------------------------------------------------------------
-- $Id$
-- --------------------------------------------------------------------------------------------

local sep = ":"
local path = KEYS[1]
local parentPathElement
local resourcesPrefix = ARGV[1]
local collectionsPrefix = ARGV[2]
local expirableSet = ARGV[3]
local timestamp = tonumber(ARGV[4])
local maxtime = tonumber(ARGV[5])
local subResources = ARGV[6]
local subResourcesCount = tonumber(ARGV[7])

local function splitToTable(divider,str)
    if (divider=='') then return false end
    local pos,arr = 0,{}
    for st,sp in function() return string.find(str,divider,pos,true) end do
        table.insert(arr,string.sub(str,pos,st-1))
        pos = sp + 1
    end
    table.insert(arr,string.sub(str,pos))
    return arr
end

local function isCollection(resName)
    if(string.find(resName, "/", -1) ~= nil) then
        return true
    end
    return false
end

local result = {}
local subResourcesTable = splitToTable(";", subResources);

for i=1,subResourcesCount do
    local subResName = subResourcesTable[i]
    if(isCollection(subResName)) then
        subResName = string.sub(subResName, 1, string.len(subResName)-1)
        local colPath = collectionsPrefix..path..sep..subResName
        if redis.call('exists',colPath) == 1 then
            local colMembers = redis.call('zrangebyscore',colPath, timestamp, maxtime)
            for k, v in ipairs(colMembers) do
                if redis.call('exists',colPath..sep..v) == 1 then
                    colMembers[k] = v.."/"
                end
            end
            table.insert(result, {subResName, cjson.encode(colMembers)})
        end
    else
        local resPath = resourcesPrefix..path..sep..subResName
        if redis.call('exists',resPath) == 1 then
            local score = tonumber(redis.call('zscore',expirableSet,resPath))
            if score == nil or score > timestamp then
                local res = (redis.call('hget',resPath,'resource'))
                if(res) then
                    table.insert(result, {subResName, res})
                end
            end
        end
    end
end

local resEncoded = cjson.encode(result)

if (resEncoded=='{}') then
    return "notFound"
end

return resEncoded