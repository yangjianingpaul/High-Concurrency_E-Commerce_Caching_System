-- =============================================
-- Distributed Lock Release Lua Script  
-- =============================================
-- This script safely releases a distributed lock with ownership verification
-- to prevent accidental lock deletion by threads that don't own the lock.
--
-- Parameters:
--   KEYS[1]: The Redis key representing the lock
--   ARGV[1]: The thread/process identifier (owner ID) 
--
-- Return Values:
--   1: Lock successfully released by the owner
--   0: Lock not released (either doesn't exist or owned by different thread)
--
-- Why use Lua script:
--   - Ensures atomicity of GET + DELETE operations
--   - Prevents race conditions between lock check and deletion
--   - Solves the "accidental lock deletion" problem in distributed systems
-- =============================================

-- Get the current lock owner identifier
local lockOwner = redis.call('get', KEYS[1])

-- Verify lock ownership before deletion
if(lockOwner == ARGV[1]) then
    -- Lock belongs to the requesting thread, safe to delete
    return redis.call('del', KEYS[1])
end

-- Lock doesn't exist or belongs to another thread
-- Return 0 to indicate no action was taken
return 0