# JMeter Quick Start Guide - E-Commerce Performance Testing

**Date:** 2025-10-20
**JMeter Version:** 5.6.3
**Server:** Ubuntu 24.04 LTS @ 192.168.1.12

---

## Overview

This guide shows you how to create and run performance tests for the e-commerce seckill system using Apache JMeter.

**Test Data Available:**
- 102,005 users
- 50,114 shops
- 110 seckill vouchers (1,010,000 total stock)

**Target Performance:**
- Concurrent Users: 5,000-8,000
- TPS: ~6,000
- P99 Response Time: <200ms

---

## Quick Start - Run a Simple Test

### Option A: Command-Line Test (Fastest)

**Test the application health endpoint:**

```bash
ssh testuser@perf-test-server

# Navigate to JMeter bin directory
cd ~/apache-jmeter-5.6.3/bin

# Run a simple load test (100 threads, 10 loops)
./jmeter -n -t ~/jmeter-tests/simple-health-check.jmx \
  -l ~/jmeter-tests/results/health-test-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o ~/jmeter-tests/reports/health-test-$(date +%Y%m%d-%H%M%S)
```

**First, we need to create the test plan. See "Creating Test Plans" below.**

---

## Creating Test Plans

### Method 1: Using JMeter GUI (Recommended for Beginners)

JMeter has a GUI that makes it easy to create test plans. However, **you need a graphical environment** to use it.

**On Your MacBook (recommended):**

1. **Download JMeter on your MacBook:**
   ```bash
   cd ~/Downloads
   curl -O https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.3.tgz
   tar -xzf apache-jmeter-5.6.3.tgz
   ```

2. **Start JMeter GUI:**
   ```bash
   cd ~/Downloads/apache-jmeter-5.6.3/bin
   ./jmeter
   ```

3. **Create a Simple Health Check Test:**
   - Right-click "Test Plan" → Add → Threads (Users) → Thread Group
     - **Number of Threads:** 100
     - **Ramp-up Period:** 10 seconds
     - **Loop Count:** 10

   - Right-click "Thread Group" → Add → Sampler → HTTP Request
     - **Server Name or IP:** 192.168.1.12
     - **Port:** 8081
     - **Path:** /actuator/health

   - Right-click "Thread Group" → Add → Listener → Summary Report
   - Right-click "Thread Group" → Add → Listener → View Results Tree

4. **Save the Test Plan:**
   - File → Save As → `health-check-test.jmx`

5. **Run the Test:**
   - Click the green "Start" button (▶)
   - Watch results in "Summary Report" and "View Results Tree"

6. **Transfer to Server:**
   ```bash
   scp ~/Downloads/health-check-test.jmx testuser@perf-test-server:~/jmeter-tests/
   ```

---

### Method 2: Manual XML Creation (Advanced)

If you prefer, you can create test plans directly by editing XML files. Here's a basic template:

**Save this as `~/jmeter-tests/basic-test.jmx` on the server:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Basic Health Check" enabled="true">
      <stringProp name="TestPlan.comments">Simple health check test</stringProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <stringProp name="LoopController.loops">10</stringProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">100</stringProp>
        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Health Check" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" enabled="true">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8081</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/actuator/health</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

**Create this file:**
```bash
ssh testuser@perf-test-server
cat > ~/jmeter-tests/basic-test.jmx << 'EOF'
[paste the XML above]
EOF
```

---

## Running Tests from Command Line

### Basic Execution

```bash
ssh testuser@perf-test-server
cd ~/apache-jmeter-5.6.3/bin

# Run test without GUI (non-interactive mode)
./jmeter -n \
  -t ~/jmeter-tests/basic-test.jmx \
  -l ~/jmeter-tests/results/test-results.jtl
```

**Explanation:**
- `-n` = Non-GUI mode
- `-t` = Test plan file
- `-l` = Log file (results)

### Generate HTML Report

```bash
# Run test and generate HTML report in one command
./jmeter -n \
  -t ~/jmeter-tests/basic-test.jmx \
  -l ~/jmeter-tests/results/test-$(date +%Y%m%d-%H%M%S).jtl \
  -e \
  -o ~/jmeter-tests/reports/report-$(date +%Y%m%d-%H%M%S)
```

**Explanation:**
- `-e` = Generate report dashboard after load test
- `-o` = Output folder for report
- `$(date +%Y%m%d-%H%M%S)` = Timestamp for unique filenames

### View Report

**Option 1: Transfer to MacBook and open:**
```bash
# On your MacBook
scp -r testuser@perf-test-server:~/jmeter-tests/reports/report-20251020-160000 ~/Desktop/
open ~/Desktop/report-20251020-160000/index.html
```

**Option 2: Use Python HTTP server on Linux:**
```bash
ssh testuser@perf-test-server
cd ~/jmeter-tests/reports/report-20251020-160000
python3 -m http.server 8888

# Then open in browser on MacBook:
# http://192.168.1.12:8888
```

---

## Test Scenarios

### Scenario 1: Simple Health Check

**Purpose:** Verify system can handle concurrent requests
**Threads:** 100-1000
**Duration:** 1-5 minutes
**Endpoint:** `GET /actuator/health`

**Create in JMeter GUI:**
1. Thread Group → 100 threads, 10s ramp-up, 100 loops
2. HTTP Request → localhost:8081 → /actuator/health → GET
3. Listeners → Summary Report, Aggregate Report

---

### Scenario 2: Shop Query Test (Cache Performance)

**Purpose:** Test cache hit rate and query performance
**Threads:** 500-2000
**Duration:** 5 minutes
**Endpoint:** `GET /shop/{id}`

**Configuration:**
- Thread Group → 500 threads, 30s ramp-up, infinite loop
- Duration → 300 seconds (5 minutes)
- HTTP Request → `GET /shop/${__Random(1,50114)}`
  - Uses random shop ID from 1 to 50,114

**Expected Results:**
- QPS: 10,000-35,000
- P99 Latency: <50ms
- Cache Hit Rate: >90%

---

### Scenario 3: Seckill Test (HIGH CONCURRENCY)

**Purpose:** Test flash sale performance under extreme load
**Threads:** 1000-8000
**Duration:** 30-60 seconds
**Endpoint:** `POST /voucher-order/seckill/{voucherId}`

**⚠️ IMPORTANT:** This endpoint requires authentication. You'll need to:

1. **Option A: Simplify for testing** - Temporarily disable authentication (NOT recommended for production)
2. **Option B: Implement login flow** - Add login steps to get token, then use token

**For now, we'll focus on Scenario 1 and 2 which don't require authentication.**

---

## Monitoring During Tests

### 1. Watch Grafana in Real-Time

Open Grafana JVM dashboard: http://192.168.1.12:3000

**Monitor:**
- Heap usage (should not grow continuously - indicates memory leak)
- GC activity (frequent GCs indicate memory pressure)
- Thread count (should not exceed max threads)
- CPU usage

### 2. Watch Docker Stats

```bash
ssh testuser@perf-test-server
docker stats
```

**Watch for:**
- CPU% - Should stay below 80%
- MEM USAGE - Should not hit limit
- NET I/O - Network throughput

### 3. Watch Application Logs

```bash
ssh testuser@perf-test-server
docker logs ecommerce-app-1 -f
```

**Look for:**
- Error messages
- Timeout warnings
- Database connection issues

---

## Quick Test Run - Step by Step

Let's run a simple test right now:

### Step 1: Create Test Plan

```bash
ssh testuser@perf-test-server

cat > ~/jmeter-tests/shop-query-test.jmx << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Shop Query Test">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Shop Queries">
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <stringProp name="LoopController.loops">100</stringProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">50</stringProp>
        <stringProp name="ThreadGroup.ramp_time">5</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Get Shop">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8081</stringProp>
          <stringProp name="HTTPSampler.path">/shop/${__Random(1,100)}</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
EOF
```

### Step 2: Run the Test

```bash
cd ~/apache-jmeter-5.6.3/bin

./jmeter -n \
  -t ~/jmeter-tests/shop-query-test.jmx \
  -l ~/jmeter-tests/results/shop-test-$(date +%Y%m%d-%H%M%S).jtl \
  -e \
  -o ~/jmeter-tests/reports/shop-test-$(date +%Y%m%d-%H%M%S)
```

### Step 3: View Results

```bash
# Find the latest report directory
ls -lt ~/jmeter-tests/reports/ | head -5

# Start HTTP server
cd ~/jmeter-tests/reports/shop-test-XXXXXX-XXXXXX
python3 -m http.server 8888
```

**Then open in browser:** http://192.168.1.12:8888

---

## Understanding JMeter Results

### Key Metrics

**Throughput (TPS/QPS):**
- Transactions (or Queries) Per Second
- Higher is better
- Target: 1,000-10,000+ depending on operation

**Response Time:**
- **Average:** Mean response time
- **P90:** 90% of requests complete within this time
- **P95:** 95% of requests complete within this time
- **P99:** 99% of requests complete within this time (most important)
- Target: <200ms for P99

**Error Rate:**
- Percentage of failed requests
- Target: <1%
- 0% is ideal

**Standard Deviation:**
- Variation in response times
- Lower is better (more consistent performance)

---

## Performance Testing Best Practices

### 1. Start Small, Scale Up

```
Test 1: 10 threads   → Baseline
Test 2: 50 threads   → 5x load
Test 3: 100 threads  → 10x load
Test 4: 500 threads  → 50x load
Test 5: 1000 threads → 100x load
Test 6: Find breaking point
```

### 2. Monitor System Resources

- CPU usage (should stay below 80%)
- Memory usage (no memory leaks)
- Network throughput
- Database connections
- Cache hit rate

### 3. Run Tests Multiple Times

- Results can vary due to:
  - Cache warming
  - Database query plan changes
  - Network conditions
  - Background processes

**Best practice:** Run each test 3 times, take average

### 4. Isolate Variables

Test one thing at a time:
- First: Test health endpoint (baseline system capacity)
- Then: Test cache queries (cache performance)
- Then: Test database queries (database performance)
- Finally: Test complex flows (end-to-end)

### 5. Record Baseline

Before making changes, record baseline performance:
- Save test results
- Document system configuration
- Note any issues or bottlenecks
- Use as comparison for future tests

---

## Troubleshooting

### Issue: "Connection Refused"

**Cause:** Application not running or wrong port

**Fix:**
```bash
curl http://localhost:8081/actuator/health
# Should return: {"status":"UP"}
```

### Issue: High Error Rate

**Causes:**
- Too many concurrent requests (exceeding capacity)
- Database connection pool exhausted
- Application crashes under load

**Fix:**
1. Reduce thread count
2. Increase ramp-up time
3. Check application logs: `docker logs ecommerce-app-1 -f`

### Issue: "OutOfMemoryError"

**Cause:** JVM heap exhausted

**Fix:**
```bash
# Check heap usage in Grafana
# If consistently high, increase heap size in docker-compose.production.yml
# JAVA_OPTS: -Xms2g -Xmx6g  (increase from 4g to 6g)
```

### Issue: Test Takes Forever

**Cause:** Too many threads or loops

**Fix:**
- Reduce thread count
- Reduce loop count
- Add constant timer between requests

---

## Next Steps

### After Basic Tests Work

1. **Create more realistic test scenarios**
   - Mix of different operations
   - Variable load patterns
   - Think time between requests

2. **Run endurance tests**
   - Long-duration tests (2-8 hours)
   - Monitor for memory leaks
   - Check for performance degradation over time

3. **Run stress tests**
   - Push system beyond normal capacity
   - Find breaking point
   - Verify graceful degradation

4. **Optimize and re-test**
   - Identify bottlenecks from test results
   - Make configuration changes
   - Re-run tests to measure improvement

---

## Quick Command Reference

```bash
# Run test
cd ~/apache-jmeter-5.6.3/bin
./jmeter -n -t ~/jmeter-tests/test.jmx -l ~/jmeter-tests/results/result.jtl

# Run test with HTML report
./jmeter -n -t ~/jmeter-tests/test.jmx \
  -l ~/jmeter-tests/results/result.jtl \
  -e -o ~/jmeter-tests/reports/report-$(date +%Y%m%d-%H%M%S)

# View report
cd ~/jmeter-tests/reports/[report-dir]
python3 -m http.server 8888
# Open: http://192.168.1.12:8888

# Transfer report to MacBook
scp -r testuser@perf-test-server:~/jmeter-tests/reports/[report-dir] ~/Desktop/

# Monitor during test
docker stats                     # Resource usage
docker logs ecommerce-app-1 -f   # Application logs
# Grafana: http://192.168.1.12:3000
```

---

## Resources

**JMeter Documentation:**
- User Manual: https://jmeter.apache.org/usermanual/
- Best Practices: https://jmeter.apache.org/usermanual/best-practices.html
- Functions: https://jmeter.apache.org/usermanual/functions.html

**Project Documentation:**
- `PERFORMANCE_TEST_PC_LINUX.md` - Detailed performance testing guide
- `PRODUCTION_REBUILD_GUIDE.md` - System rebuild guide
- `NEXT_STEPS_GUIDE.md` - What to do next

---

**Happy Testing! 🚀**
