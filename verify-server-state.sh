#!/bin/bash

# ============================================================
# Server State Verification Script
# Purpose: Verify server is ready for next setup steps
# Run this on your Linux PC after SSH is working
# ============================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
PASS=0
FAIL=0
WARN=0

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  Server State Verification for Performance Testing Setup${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""
echo "Server: $(hostname)"
echo "Date: $(date)"
echo "User: $(whoami)"
echo ""

# Function to print pass/fail
print_pass() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((PASS++))
}

print_fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((FAIL++))
}

print_warn() {
    echo -e "${YELLOW}⚠ WARN${NC}: $1"
    ((WARN++))
}

print_section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ============================================================
# 1. Basic System Information
# ============================================================
print_section "1. System Information"

# Check Ubuntu version
if [ -f /etc/os-release ]; then
    . /etc/os-release
    echo "OS: $NAME $VERSION"
    if [[ "$VERSION_ID" == "22.04" ]]; then
        print_pass "Ubuntu 22.04 LTS detected"
    else
        print_warn "Expected Ubuntu 22.04 LTS, got $VERSION_ID"
    fi
else
    print_fail "Cannot determine OS version"
fi

# Check kernel version
KERNEL=$(uname -r)
echo "Kernel: $KERNEL"
print_pass "Kernel version: $KERNEL"

# Check uptime
UPTIME=$(uptime -p)
echo "Uptime: $UPTIME"
print_pass "System is running"

# ============================================================
# 2. Hardware Verification
# ============================================================
print_section "2. Hardware Verification"

# Check CPU
CPU_MODEL=$(lscpu | grep "Model name" | cut -d':' -f2 | xargs)
CPU_CORES=$(nproc)
echo "CPU: $CPU_MODEL"
echo "CPU Cores: $CPU_CORES"

if [[ "$CPU_MODEL" == *"12600KF"* ]] || [[ "$CPU_MODEL" == *"12600K"* ]]; then
    print_pass "Intel 12600KF detected"
elif [[ "$CPU_CORES" -ge 8 ]]; then
    print_warn "Different CPU detected, but has $CPU_CORES cores (acceptable)"
else
    print_fail "CPU has only $CPU_CORES cores (minimum 8 recommended)"
fi

# Check RAM
TOTAL_RAM=$(free -h | awk '/^Mem:/ {print $2}')
TOTAL_RAM_GB=$(free -g | awk '/^Mem:/ {print $2}')
echo "Total RAM: $TOTAL_RAM"

if [ "$TOTAL_RAM_GB" -ge 30 ]; then
    print_pass "RAM: $TOTAL_RAM (≥32GB)"
elif [ "$TOTAL_RAM_GB" -ge 15 ]; then
    print_warn "RAM: $TOTAL_RAM (16GB+, but 32GB recommended)"
else
    print_fail "RAM: $TOTAL_RAM (insufficient, 32GB required)"
fi

# Check NVMe storage
echo ""
echo "Storage devices:"
lsblk -d -o NAME,SIZE,TYPE,MOUNTPOINT | grep -E "nvme|sda|sdb"

if lsblk | grep -q nvme0n1; then
    NVME_SIZE=$(lsblk -b -d -o SIZE -n /dev/nvme0n1 | awk '{print $1/1024/1024/1024 "GB"}')
    echo "NVMe: /dev/nvme0n1 ($NVME_SIZE)"
    print_pass "NVMe SSD detected"

    # Check if NVMe is mounted as root
    if mount | grep -q "nvme0n1.*on / "; then
        print_pass "NVMe is mounted as root filesystem"
    else
        print_warn "NVMe not mounted as root (check if OS is on NVMe)"
    fi
else
    print_warn "NVMe not detected (system may be on SATA/USB)"
fi

# Check available disk space
ROOT_SPACE=$(df -h / | awk 'NR==2 {print $4}')
ROOT_SPACE_GB=$(df -BG / | awk 'NR==2 {print $4}' | sed 's/G//')
echo "Available space on /: $ROOT_SPACE"

if [ "$ROOT_SPACE_GB" -ge 100 ]; then
    print_pass "Sufficient disk space: $ROOT_SPACE available"
elif [ "$ROOT_SPACE_GB" -ge 50 ]; then
    print_warn "Disk space: $ROOT_SPACE (50GB+, but 100GB+ recommended)"
else
    print_fail "Insufficient disk space: $ROOT_SPACE (need 100GB+)"
fi

# ============================================================
# 3. Network Configuration
# ============================================================
print_section "3. Network Configuration"

# Get IP address
IP_ADDR=$(ip -4 addr show | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v '127.0.0.1' | head -1)
echo "IP Address: $IP_ADDR"

if [ -n "$IP_ADDR" ]; then
    print_pass "Network configured: $IP_ADDR"
else
    print_fail "No IP address detected"
fi

# Check internet connectivity
if ping -c 2 8.8.8.8 > /dev/null 2>&1; then
    print_pass "Internet connectivity working"
else
    print_fail "No internet connectivity"
fi

# Check DNS resolution
if ping -c 2 google.com > /dev/null 2>&1; then
    print_pass "DNS resolution working"
else
    print_fail "DNS resolution not working"
fi

# Check SSH service
if systemctl is-active --quiet ssh; then
    print_pass "SSH service is running"
else
    print_fail "SSH service is not running"
fi

# ============================================================
# 4. User and Permissions
# ============================================================
print_section "4. User and Permissions"

# Check current user
CURRENT_USER=$(whoami)
echo "Current user: $CURRENT_USER"

# Check sudo access
if sudo -n true 2>/dev/null; then
    print_pass "User has passwordless sudo (already configured)"
elif sudo -v 2>/dev/null; then
    print_pass "User has sudo access"
else
    print_fail "User does not have sudo access"
fi

# Check user groups
USER_GROUPS=$(groups)
echo "User groups: $USER_GROUPS"

if [[ "$USER_GROUPS" == *"sudo"* ]] || [[ "$USER_GROUPS" == *"wheel"* ]]; then
    print_pass "User in sudo/wheel group"
else
    print_warn "User not in sudo/wheel group"
fi

# ============================================================
# 5. System Updates
# ============================================================
print_section "5. System Updates"

# Check if system needs updates
UPDATES_AVAILABLE=$(apt list --upgradable 2>/dev/null | grep -c upgradable)
echo "Packages that can be upgraded: $UPDATES_AVAILABLE"

if [ "$UPDATES_AVAILABLE" -eq 0 ]; then
    print_pass "System is up to date"
elif [ "$UPDATES_AVAILABLE" -lt 50 ]; then
    print_warn "$UPDATES_AVAILABLE packages need updating (run: sudo apt update && sudo apt upgrade)"
else
    print_fail "$UPDATES_AVAILABLE packages need updating (please update system first)"
fi

# Check if reboot is required
if [ -f /var/run/reboot-required ]; then
    print_warn "System reboot required (after kernel updates)"
else
    print_pass "No reboot required"
fi

# ============================================================
# 6. Essential Tools
# ============================================================
print_section "6. Essential Tools"

# Check for essential commands
TOOLS=("curl" "wget" "git" "vim" "htop")
for tool in "${TOOLS[@]}"; do
    if command -v "$tool" &> /dev/null; then
        print_pass "$tool is installed"
    else
        print_warn "$tool is not installed (will install later)"
    fi
done

# ============================================================
# 7. Docker Status
# ============================================================
print_section "7. Docker Status"

if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version | cut -d' ' -f3 | sed 's/,//')
    print_pass "Docker is installed (version: $DOCKER_VERSION)"

    # Check if Docker is running
    if systemctl is-active --quiet docker; then
        print_pass "Docker service is running"
    else
        print_warn "Docker service is not running"
    fi

    # Check if user is in docker group
    if groups | grep -q docker; then
        print_pass "User is in docker group"

        # Test docker command without sudo
        if docker ps &> /dev/null; then
            print_pass "Can run docker commands without sudo"
        else
            print_warn "Cannot run docker without sudo (may need to logout/login)"
        fi
    else
        print_warn "User not in docker group (will configure later)"
    fi
else
    print_warn "Docker not installed (will install in next steps)"
fi

# Check Docker Compose
if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    COMPOSE_VERSION=$(docker compose version --short)
    print_pass "Docker Compose is installed (version: $COMPOSE_VERSION)"
else
    print_warn "Docker Compose not installed (will install with Docker)"
fi

# ============================================================
# 8. Performance Settings (Not Applied Yet - Expected)
# ============================================================
print_section "8. Performance Settings (Preview)"

echo "These will be configured in next steps:"
echo ""

# Check swappiness
SWAPPINESS=$(cat /proc/sys/vm/swappiness)
echo "• vm.swappiness: $SWAPPINESS (target: 10)"
if [ "$SWAPPINESS" -eq 10 ]; then
    print_pass "Swappiness already optimized"
else
    echo "  Will be configured in Section 5.1"
fi

# Check THP
THP_ENABLED=$(cat /sys/kernel/mm/transparent_hugepage/enabled | grep -o '\[.*\]' | tr -d '[]')
echo "• Transparent Huge Pages: $THP_ENABLED (target: never)"
if [ "$THP_ENABLED" == "never" ]; then
    print_pass "THP already disabled"
else
    echo "  Will be configured in Section 5.3"
fi

# Check I/O scheduler for NVMe
if [ -f /sys/block/nvme0n1/queue/scheduler ]; then
    IO_SCHED=$(cat /sys/block/nvme0n1/queue/scheduler | grep -o '\[.*\]' | tr -d '[]')
    echo "• I/O Scheduler (NVMe): $IO_SCHED (target: none)"
    if [ "$IO_SCHED" == "none" ]; then
        print_pass "I/O scheduler already optimized for NVMe"
    else
        echo "  Will be configured in Section 5.6"
    fi
fi

# ============================================================
# 9. Java and Maven (for project build)
# ============================================================
print_section "9. Java and Maven"

if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    print_pass "Java is installed (version: $JAVA_VERSION)"

    if [[ "$JAVA_VERSION" == "17"* ]]; then
        print_pass "Java 17 detected (correct version)"
    else
        print_warn "Java version is $JAVA_VERSION (need Java 17)"
    fi
else
    print_warn "Java not installed (will install in Section 7.1)"
fi

if command -v mvn &> /dev/null; then
    MAVEN_VERSION=$(mvn -version | head -n 1 | cut -d' ' -f3)
    print_pass "Maven is installed (version: $MAVEN_VERSION)"
else
    print_warn "Maven not installed (will install in Section 7.1)"
fi

# ============================================================
# 10. Firewall Status
# ============================================================
print_section "10. Firewall Status"

if command -v ufw &> /dev/null; then
    UFW_STATUS=$(sudo ufw status | head -n 1 | cut -d':' -f2 | xargs)
    echo "UFW Status: $UFW_STATUS"

    if [ "$UFW_STATUS" == "active" ]; then
        print_warn "Firewall is active (will configure ports in Section 3.4)"

        # Check if SSH is allowed
        if sudo ufw status | grep -q "22.*ALLOW"; then
            print_pass "SSH port (22) is allowed"
        else
            print_fail "SSH port (22) is NOT allowed (could lock you out!)"
        fi
    else
        echo "  Firewall will be configured in Section 3.4"
    fi
else
    print_warn "UFW not installed (will install with essential tools)"
fi

# ============================================================
# Summary
# ============================================================
print_section "VERIFICATION SUMMARY"

echo ""
echo -e "${GREEN}Passed checks: $PASS${NC}"
echo -e "${YELLOW}Warnings: $WARN${NC}"
echo -e "${RED}Failed checks: $FAIL${NC}"
echo ""

# Overall status
if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}✓ SERVER IS READY FOR NEXT STEPS!${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "Your server is in good state. You can proceed with:"
    echo ""
    echo "NEXT STEPS:"
    echo "  1. Continue from Section 3: Initial System Configuration"
    echo "     → sudo apt update && sudo apt upgrade -y"
    echo "     → Install essential tools"
    echo ""
    echo "  2. Then proceed to Section 4: SSH Setup (already working!)"
    echo "     → Setup SSH key authentication (recommended)"
    echo ""
    echo "  3. Then Section 5: System Performance Tuning"
    echo "     → Kernel parameters optimization"
    echo "     → CPU governor configuration"
    echo ""
    echo "To continue, follow ENVIRONMENT_SETUP_GUIDE.md from Section 3.1"

elif [ $FAIL -lt 3 ]; then
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}⚠ SERVER IS MOSTLY READY WITH MINOR ISSUES${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "You can proceed, but please address the failed checks above."
    echo "Most issues will be resolved in upcoming setup steps."

else
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${RED}✗ CRITICAL ISSUES DETECTED${NC}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "Please resolve the failed checks before proceeding."
    echo "Review ENVIRONMENT_SETUP_GUIDE.md sections for solutions."
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo "Verification completed at: $(date)"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
