# Function Realization

## Shared session login based on Redis

![](/resources/redisImplementLogin.png)

## Resolved the status login refresh problem

![](/resources/interceptor.png)

## Merchant query cache

![](/resources/redisCache.png)

## Cache update strategy

- The database and cache are inconsistent
    - First operate the database, and then delete the cache, the reason is that if you choose the first scheme, in the two threads to access concurrently, suppose thread 1 first, he deleted the cache, at this time, thread 2 comes, he queries the cache data does not exist, at this time he writes to the cache, when he writes to the cache, thread 1 and then perform the update action, in fact, write is the old data, The new data is overwritten by the old data.

## Cache penetration problem

- Bloom filtration
- Cache empty object

![](/resources/CacheBreakdown.png)

## Cache breakdown problem

- Mutex

![](/resources/mutex.png)

- Logical expiration

![](/resources/logic_expired.png)

## couponsec

- redis implements a globally unique id

~~~java
@Component
public class RedisIdWorker {
    /**
     * Start time stamp
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * Number of digits
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.Generate timestamp
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.Generate sequence number
        // 2.1.Gets the current date, accurate to day
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.auto increment
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.Concatenate and return
        return timestamp << COUNT_BITS | count;
    }
}
~~~

- Second order

![](/resources/seckill.png)

- Optimistic lock to solve oversold problems

~~~java
boolean success = seckillVoucherService.update()
            .setSql("stock= stock -1")
            .eq("voucher_id", voucherId).update().gt("stock",0); //where id = ? and stock > 0
~~~

- One coupon for one person

![](/resources/doubleOrdering.png)

## Distributed lock
- Using redis setNx method, when there are multiple threads enter, we use this method, when the first thread enters, redis has this key, and returns 1, if the result is 1, it means that he has captured the lock, then he goes to execute business, and then delete the lock, exit the lock logic, no brother who has captured the lock, Wait for some time and try again.

- Solve the problem of distributed lock deletion by mistake:
    - The thread identifier is stored when the lock is acquired (can be represented by UUID). The thread identifier in the lock is obtained when the lock is released to determine whether it is consistent with the current thread identifier
    - If consistent, release the lock
    - If inconsistent, the lock is not released

## Lua scripts solve the atomicity problem of multiple commands

- Using Java code to invoke Lua scripts to transform distributed locks

~~~java
private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

public void unlock() {
    // Invoke the lua script
    stringRedisTemplate.execute(
            UNLOCK_SCRIPT,
            Collections.singletonList(KEY_PREFIX + name),
            ID_PREFIX + Thread.currentThread().getId());
}
~~~

## Instant kill optimization: asynchronous instant kill

![](/resources/seckillOptimization.png)

## redis message Queue - based on stream

- Create a Consumer group:

~~~shell
XGROUP CREATE key groupName ID [MKSTREAM]
~~~

- Delete a specified consumer group:

~~~shell
XGROUP DESTROY key groupName
~~~

- To add consumers to a specified consumer group:

~~~shell
XGROUP CREATECONSUMER key groupname consumername
~~~

- To delete a specified consumer from a consumer group:

~~~shell
XGROUP DELCONSUMER key groupname consumername
~~~

- Read messages from Consumer groups:

~~~shell
XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds] [NOACK] STREAMS key [key ...] ID [ID ...]
~~~

## Notes from the expert shop

- Shop notes are similar to the evaluation of review sites, often a combination of graphics. There are two corresponding tables: tb_blog: list of notes, including the title of the notes, text, pictures, etc. tb_blog_comments: other users' comments on the notes
- Check the scout notes
- Like function
- Like leader board

## Friend follow
- Follow and unfollow
- mutual followed account
- feed stream, push to fan inbox
- feed stream, to achieve paging query mailbox

## Nearby business
- GEO is the short form of Geolocation, which stands for geographic coordinates. Redis added support for GEO in version 3.2, allowing the storage of geographic coordinate information to help us retrieve data based on latitude and longitude. Common commands are:
    - GEOADD: Adds a piece of geospatial information, including longitude, latitude, member
    - GEODIST: Calculates the distance between the specified two points and returns it
    - GEOHASH: Converts the coordinates of the specified member into a hash string and returns it
    - GEOPOS: Returns the coordinates of the specified member
    - GEORADIUS: Specifies the center and radius of the circle, finds all the members contained in the circle, and returns them by the distance from the center of the circle. 6. It has since been abandoned
    - GEOSEARCH: Searches for members within a specified range and returns them sorted by distance from the specified point. The range can be circular or rectangular. 6.2. New Features
    - GEOSEARCHSTORE: Same functionality as GEOSEARCH, but you can store the results to a specified key. 6.2. New Features

## User check-in: BitMap
- BitMap operation commands are:
    - SETBIT: Stores a 0 or 1 to the specified position (offset)
    - GETBIT: Gets the bit value of the specified position (offset)
    - BITCOUNT: Counts the number of bits whose value is 1 in the BitMap
    - BITFIELD: The value of the specified position (offset) in the bit array in the BitMap operation (query, modify, increment)
    - BITFIELD_RO: Gets the bit array in BitMap and returns it in decimal form
    - BITOP: Perform BitMap operations (with, or, or, or) on the results of multiple bitmaps.
    - BITPOS: Finds the first 0 or 1 in the specified range in the bit array

- Implement the sign-in interface and save the current user's current day sign-in information to Redis
    - We can take the year and month as the key of bitMap, and then save it in a bitMap, and change the number from 0 to 1 on the corresponding bit every time you sign in, as long as the corresponding is 1, it indicates that the day has been signed in, and otherwise there is no sign in.

## UV statistics
- UV: The full name of Unique Visitor, also called the number of independent visitors, refers to the natural person who visits and views this web page through the Internet. If the same user visits the website multiple times in a day, only one time is recorded.
- PV: Full name Page View, also known as page visits or clicks, each user visits a page of the website, record 1 PV, the user opens the page many times, then record multiple PV. Often used to measure traffic to a website.

~~~java
@Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j=0;
        for (int i=0;i<1000000;i++) {
            j=i%1000;
            values[j] = "user_" + i;
            if (j==999) {
//                Send to redis
                stringRedisTemplate.opsForHyperLogLog().add("h12", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("h12");
        System.out.println("count = " + count);
    }
~~~