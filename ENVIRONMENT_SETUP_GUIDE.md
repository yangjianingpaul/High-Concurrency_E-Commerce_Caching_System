# Production Environment Setup Guide

**Hardware Configuration:**
- CPU: Intel Core i5-12600KF (10 cores: 6P-cores + 4E-cores)
- RAM: 32GB DDR4 3600MHz
- Storage: 1TB NVMe M.2 SSD
- Network: Gigabit Ethernet (assumed)

**Target:** Complete production-grade performance testing environment with remote SSH access from MacBook

**Total Setup Time:** ~2-3 hours

---

## Table of Contents

1. [Pre-Installation Preparation](#1-pre-installation-preparation)
2. [Ubuntu Server Installation](#2-ubuntu-server-installation)
3. [Initial System Configuration](#3-initial-system-configuration)
4. [SSH Setup for MacBook Access](#4-ssh-setup-for-macbook-access)
5. [System Performance Tuning](#5-system-performance-tuning)
6. [Docker Installation & Configuration](#6-docker-installation--configuration)
7. [Project Setup](#7-project-setup)
8. [Production Infrastructure Deployment](#8-production-infrastructure-deployment)
9. [MySQL Optimization](#9-mysql-optimization)
10. [Redis Cluster Optimization](#10-redis-cluster-optimization)
11. [Monitoring Setup](#11-monitoring-setup)
12. [Verification & Testing](#12-verification--testing)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Pre-Installation Preparation

### 1.1 Download Ubuntu Server

**On your MacBook:**

```bash
# Download Ubuntu Server 22.04.3 LTS (recommended)
# Visit: https://ubuntu.com/download/server
# Or use direct link:
cd ~/Downloads
curl -L -o ubuntu-22.04.3-live-server-amd64.iso \
  https://releases.ubuntu.com/22.04.3/ubuntu-22.04.3-live-server-amd64.iso

# Verify download (optional)
shasum -a 256 ubuntu-22.04.3-live-server-amd64.iso
# Compare with official SHA256 from Ubuntu website
```

**File size:** ~2.5GB
**Download time:** ~5-15 minutes (depending on internet speed)

### 1.2 Create Bootable USB Drive

**Requirements:**
- USB drive (8GB minimum, will be erased!)
- USB drive will be formatted - backup any data first

**On MacBook:**

```bash
# 1. Insert USB drive into MacBook

# 2. Find USB drive identifier
diskutil list
# Look for your USB drive (usually /dev/disk2 or /dev/disk3)
# Note the identifier (e.g., disk2)

# ⚠️ WARNING: Double-check the disk number!
# The following command will ERASE the USB drive completely!

# 3. Unmount the USB drive (replace diskN with your disk number)
diskutil unmountDisk /dev/diskN

# 4. Write ISO to USB (THIS WILL ERASE THE USB!)
# Replace diskN with your USB disk number (e.g., disk2)
sudo dd if=~/Downloads/ubuntu-22.04.3-live-server-amd64.iso \
  of=/dev/rdiskN bs=1m

# This will take 5-10 minutes with no progress indication
# Be patient! You can press Ctrl+T to see progress

# 5. When complete, eject USB
diskutil eject /dev/diskN

# You may see "The disk you inserted was not readable by this computer"
# This is normal - Ubuntu filesystem is not readable on macOS
# Click "Eject" if prompted
```

**Alternative: Use balenaEtcher (GUI method)**

```bash
# Install balenaEtcher
brew install --cask balenaetcher

# Open balenaEtcher
open -a balenaEtcher

# Steps in balenaEtcher:
# 1. Select ubuntu-22.04.3-live-server-amd64.iso
# 2. Select your USB drive
# 3. Click "Flash!"
# 4. Wait ~5 minutes
```

### 1.3 Prepare PC BIOS Settings

**Before installation, configure BIOS:**

1. **Insert USB drive into your PC**
2. **Restart PC and enter BIOS** (usually press F2, F12, or Del during boot)
3. **Configure the following:**

```
Boot Settings:
├─ Boot Mode: UEFI (not Legacy/CSM)
├─ Secure Boot: Disabled
├─ Fast Boot: Disabled
└─ Boot Priority: USB Drive first

CPU Settings (if available):
├─ Intel Turbo Boost: Enabled
├─ Intel Hyper-Threading: Enabled
└─ CPU Power Management: Performance mode

RAM Settings:
├─ XMP Profile: Enabled (to use 3600MHz)
└─ Verify RAM shows 3600MHz in BIOS

Storage:
├─ SATA Mode: AHCI
├─ NVMe Support: Enabled
└─ Verify 1TB NVMe is detected
```

4. **Save and Exit** (usually F10)
5. **Boot from USB** (may need to press F12 for boot menu)

---

## 2. Ubuntu Server Installation

### 2.1 Ubuntu Installer Walkthrough

**Step-by-step installation:**

#### Screen 1: Language Selection
```
Select: English
Press: Enter
```

#### Screen 2: Keyboard Configuration
```
Layout: English (US)  # Or your preferred layout
Variant: English (US)
Press: Done
```

#### Screen 3: Installation Type
```
Select: Ubuntu Server
Press: Done
```

#### Screen 4: Network Configuration

**IMPORTANT: Configure static IP for SSH access**

```
# If you see network interface (e.g., eno1, enp3s0):

Option 1: DHCP (Easier, recommended for home network)
├─ Leave as default (DHCPv4)
├─ Note the IP address shown (e.g., 192.168.1.100)
└─ Press: Done

Option 2: Static IP (Better for production)
├─ Select your network interface (e.g., eno1)
├─ Press: Enter
├─ Select: Edit IPv4
├─ Select: Manual
├─ Fill in:
│   Subnet: 192.168.1.0/24          # Adjust to your network
│   Address: 192.168.1.100          # Choose static IP
│   Gateway: 192.168.1.1            # Your router IP
│   Name servers: 8.8.8.8,8.8.4.4  # Google DNS
└─ Press: Save, then Done

# ⚠️ NOTE: Write down this IP address - you'll need it for SSH!
```

#### Screen 5: Proxy Configuration
```
Proxy address: [leave blank]
Press: Done
```

#### Screen 6: Ubuntu Archive Mirror
```
Leave default: http://archive.ubuntu.com/ubuntu
Press: Done
```

#### Screen 7: Storage Configuration

**IMPORTANT: Use entire NVMe disk**

```
Storage layout: Use entire disk

Select: /dev/nvme0n1 (1TB NVMe)

Setup options:
├─ [X] Use entire disk
├─ [ ] Setup this disk as an LVM group (don't select)
└─ [ ] Encrypt this disk with LUKS (don't select for testing)

Partition layout will show:
├─ /dev/nvme0n1p1: 1GB   (EFI System Partition)
├─ /dev/nvme0n1p2: 2GB   (Boot partition)
└─ /dev/nvme0n1p3: 997GB (Root filesystem /)

Press: Done
Confirm: Continue (destructive action warning)
```

#### Screen 8: Profile Setup

**CRITICAL: Remember these credentials!**

```
Your name: Performance Tester
Your server's name: perf-test-server
Pick a username: testuser          # ← You'll use this for SSH
Choose a password: [Strong Password]  # ← Remember this!
Confirm your password: [Same Password]

⚠️ WRITE DOWN:
Username: testuser
Password: _______________
Server name: perf-test-server
```

#### Screen 9: SSH Setup

**IMPORTANT: Install OpenSSH server**

```
[X] Install OpenSSH server    # ← MUST check this box!
[ ] Import SSH identity       # Leave unchecked
[ ] Allow password authentication over SSH  # Leave checked

Press: Done
```

#### Screen 10: Featured Server Snaps

**Select useful tools:**

```
[X] docker    # ← RECOMMENDED: Select this to install Docker

Other options (optional):
[ ] microk8s
[ ] lxd
[ ] etc.

Press: Done
```

#### Installation Progress

```
Installing system...
Progress: [=================    ] 75%

# This takes 10-15 minutes
# You'll see:
├─ Downloading packages
├─ Installing base system
├─ Installing selected snaps (docker)
└─ Configuring system

Wait for: "Installation complete!"
```

#### Final Screen: Complete

```
Installation complete!

[X] Reboot Now

Press: Enter

# Remove USB drive when prompted
# System will reboot into Ubuntu Server
```

### 2.2 First Boot

**After reboot:**

```
Ubuntu 22.04.3 LTS perf-test-server tty1

perf-test-server login: testuser
Password: [your password]

# Success! You'll see:
Welcome to Ubuntu 22.04.3 LTS (GNU/Linux 5.15.0-xxx-generic x86_64)

testuser@perf-test-server:~$
```

**Verify basic system info:**

```bash
# Check CPU
lscpu | grep "Model name"
# Should show: Intel(R) Core(TM) i5-12600KF

# Check RAM
free -h
# Should show: ~32GB total

# Check disk
df -h /
# Should show: ~1TB NVMe

# Check network
ip addr show
# Note your IP address (e.g., 192.168.1.100)

# Check internet connectivity
ping -c 3 google.com
# Should see replies
```

---

## 3. Initial System Configuration

### 3.1 Update System

```bash
# Update package lists
sudo apt update

# Upgrade all packages
sudo apt upgrade -y

# This may take 10-20 minutes
# Will download and install ~500MB of updates

# Reboot to apply kernel updates
sudo reboot

# Wait 1 minute, then SSH back in (from MacBook)
```

### 3.2 Install Essential Tools

```bash
# After reboot, SSH back in and install tools
sudo apt install -y \
    vim \
    htop \
    iotop \
    net-tools \
    curl \
    wget \
    git \
    build-essential \
    software-properties-common \
    apt-transport-https \
    ca-certificates \
    gnupg \
    lsb-release \
    sysstat \
    iperf3 \
    nload \
    ncdu \
    tree \
    jq \
    unzip

# Verify installations
vim --version
git --version
htop --version
```

### 3.3 Set Timezone

```bash
# Set to your timezone (example: Asia/Shanghai)
sudo timedatectl set-timezone Asia/Shanghai

# Or use your timezone:
# Americas: America/New_York, America/Los_Angeles
# Europe: Europe/London, Europe/Paris
# Asia: Asia/Tokyo, Asia/Shanghai

# Verify
timedatectl
```

### 3.4 Configure Firewall (UFW)

```bash
# Enable firewall
sudo ufw enable

# Allow SSH (CRITICAL - do this first!)
sudo ufw allow 22/tcp comment 'SSH'

# Allow ports for our application
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'
sudo ufw allow 8081/tcp comment 'App-1'
sudo ufw allow 8082/tcp comment 'App-2'
sudo ufw allow 8083/tcp comment 'App-3'
sudo ufw allow 3000/tcp comment 'Grafana'
sudo ufw allow 9090/tcp comment 'Prometheus'
sudo ufw allow 3306/tcp comment 'MySQL'
sudo ufw allow 6379/tcp comment 'Redis'
sudo ufw allow 7000:7005/tcp comment 'Redis Cluster'

# Verify firewall status
sudo ufw status numbered

# Should show all allowed ports
```

---

## 4. SSH Setup for MacBook Access

### 4.1 Test SSH Connection from MacBook

**On your MacBook:**

```bash
# Test SSH connection
# Replace 192.168.1.100 with your PC's IP address
ssh testuser@192.168.1.100

# First connection will ask:
# "Are you sure you want to continue connecting (yes/no)?"
# Type: yes

# Enter your password when prompted
# You should now be logged into your Linux PC remotely!

# Verify you're connected
hostname
# Should show: perf-test-server

# Exit SSH
exit
```

### 4.2 Setup SSH Key Authentication (Passwordless Login)

**On your MacBook:**

```bash
# 1. Generate SSH key (if you don't have one)
# Check if you already have a key:
ls -la ~/.ssh/id_rsa.pub

# If file doesn't exist, generate new key:
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
# Press Enter for default location
# Press Enter for no passphrase (or set one for extra security)

# 2. Copy SSH key to Linux PC
ssh-copy-id testuser@192.168.1.100
# Enter your password one last time

# 3. Test passwordless login
ssh testuser@192.168.1.100
# Should login WITHOUT asking for password!

# 4. Create SSH config for easy access
cat >> ~/.ssh/config << 'EOF'
Host perf-test
    HostName 192.168.1.100
    User testuser
    Port 22
    IdentityFile ~/.ssh/id_rsa
    ServerAliveInterval 60
    ServerAliveCountMax 3
EOF

# 5. Now you can connect easily:
ssh perf-test
# No IP address or username needed!
```

### 4.3 Setup SSH Aliases (Optional but Recommended)

**On your MacBook:**

```bash
# Add to your shell profile (~/.zshrc or ~/.bashrc)
echo "alias sshperf='ssh perf-test'" >> ~/.zshrc
echo "alias scpperf='scp -r perf-test:'" >> ~/.zshrc

# Reload shell config
source ~/.zshrc

# Now you can use:
sshperf           # Connect to server
scpperf           # Copy files from server
```

---

## 5. System Performance Tuning

### 5.1 Kernel Parameter Optimization

**On Linux PC (via SSH):**

```bash
# Create performance tuning configuration
sudo tee /etc/sysctl.d/99-performance.conf > /dev/null << 'EOF'
# ============================================================
# Network Performance Optimization
# ============================================================
# Increase maximum connections
net.core.somaxconn = 65535

# Increase network buffer sizes
net.core.netdev_max_backlog = 65535
net.core.rmem_max = 134217728
net.core.wmem_max = 134217728
net.ipv4.tcp_rmem = 4096 87380 67108864
net.ipv4.tcp_wmem = 4096 65536 67108864

# TCP performance tuning
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_keepalive_time = 300
net.ipv4.tcp_keepalive_probes = 5
net.ipv4.tcp_keepalive_intvl = 15
net.ipv4.tcp_slow_start_after_idle = 0

# Enable TCP Fast Open
net.ipv4.tcp_fastopen = 3

# ============================================================
# Memory Management
# ============================================================
# Reduce swap usage (we have 32GB RAM)
vm.swappiness = 10

# Improve cache management
vm.dirty_ratio = 15
vm.dirty_background_ratio = 5

# Memory overcommit for Redis/MySQL
vm.overcommit_memory = 1

# ============================================================
# File System
# ============================================================
# Increase file descriptor limits
fs.file-max = 2097152
fs.nr_open = 2097152

# Increase inotify limits (for file watching)
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 512

# ============================================================
# Kernel
# ============================================================
# Increase PID limit
kernel.pid_max = 4194304

# Reduce kernel message logging
kernel.printk = 3 4 1 3
EOF

# Apply settings immediately
sudo sysctl -p /etc/sysctl.d/99-performance.conf

# Verify settings applied
sudo sysctl net.core.somaxconn
sudo sysctl vm.swappiness
```

### 5.2 System Limits Configuration

```bash
# Configure user limits for high performance
sudo tee -a /etc/security/limits.conf > /dev/null << 'EOF'

# ============================================================
# Performance Testing User Limits
# ============================================================
# File descriptors
*                soft    nofile          1048576
*                hard    nofile          1048576
root             soft    nofile          1048576
root             hard    nofile          1048576

# Process limits
*                soft    nproc           unlimited
*                hard    nproc           unlimited
root             soft    nproc           unlimited
root             hard    nproc           unlimited

# Memory locking (for Redis)
*                soft    memlock         unlimited
*                hard    memlock         unlimited

# Core dump size (for debugging)
*                soft    core            unlimited
*                hard    core            unlimited
EOF

# Configure systemd limits
sudo mkdir -p /etc/systemd/system.conf.d/
sudo tee /etc/systemd/system.conf.d/limits.conf > /dev/null << 'EOF'
[Manager]
DefaultLimitNOFILE=1048576
DefaultLimitNPROC=unlimited
EOF

# Reload systemd
sudo systemctl daemon-reload
```

### 5.3 Disable Transparent Huge Pages (THP)

**THP can cause performance issues with Redis and MySQL**

```bash
# Create systemd service to disable THP on boot
sudo tee /etc/systemd/system/disable-thp.service > /dev/null << 'EOF'
[Unit]
Description=Disable Transparent Huge Pages (THP)
DefaultDependencies=no
After=sysinit.target local-fs.target
Before=basic.target

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'echo never > /sys/kernel/mm/transparent_hugepage/enabled'
ExecStart=/bin/sh -c 'echo never > /sys/kernel/mm/transparent_hugepage/defrag'

[Install]
WantedBy=basic.target
EOF

# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable disable-thp.service
sudo systemctl start disable-thp.service

# Verify THP is disabled
cat /sys/kernel/mm/transparent_hugepage/enabled
# Should show: always madvise [never]
```

### 5.4 CPU Performance Tuning

```bash
# Install CPU frequency utilities
sudo apt install -y cpufrequtils linux-tools-common linux-tools-generic

# Set CPU governor to performance mode
echo 'GOVERNOR="performance"' | sudo tee /etc/default/cpufrequtils

# Apply immediately
sudo systemctl restart cpufrequtils

# Verify CPU governor
cpufreq-info | grep "current policy"
# Should show: performance

# Check CPU frequencies
sudo cpupower frequency-info
# Should show max frequencies for all cores

# Disable CPU idle states for maximum performance (optional)
sudo tee /etc/default/grub.d/performance.cfg > /dev/null << 'EOF'
# CPU performance tuning
GRUB_CMDLINE_LINUX_DEFAULT="$GRUB_CMDLINE_LINUX_DEFAULT intel_idle.max_cstate=0 processor.max_cstate=1"
EOF

# Update GRUB
sudo update-grub

# Note: Will require reboot to take effect
```

### 5.5 Disable Unnecessary Services

```bash
# List enabled services
systemctl list-unit-files --state=enabled

# Disable unnecessary services to free resources
sudo systemctl disable bluetooth.service
sudo systemctl disable cups.service
sudo systemctl disable cups-browsed.service
sudo systemctl disable ModemManager.service
sudo systemctl stop bluetooth.service
sudo systemctl stop cups.service

# Verify services are disabled
systemctl is-enabled bluetooth.service
# Should show: disabled
```

### 5.6 I/O Scheduler Optimization

```bash
# Check current I/O scheduler for NVMe
cat /sys/block/nvme0n1/queue/scheduler
# Default is usually [none] or [mq-deadline]

# For NVMe, 'none' is optimal (uses native NVMe queuing)
# If not set to none, set it:
echo none | sudo tee /sys/block/nvme0n1/queue/scheduler

# Make it persistent across reboots
sudo tee /etc/udev/rules.d/60-scheduler.rules > /dev/null << 'EOF'
# Set I/O scheduler for NVMe to none
ACTION=="add|change", KERNEL=="nvme[0-9]n[0-9]", ATTR{queue/scheduler}="none"
EOF

# Verify
cat /sys/block/nvme0n1/queue/scheduler
# Should show: [none]
```

### 5.7 Reboot to Apply All Settings

```bash
# Reboot to apply all kernel and CPU settings
sudo reboot

# Wait 1 minute, then reconnect from MacBook
ssh perf-test

# Verify settings after reboot
sudo sysctl net.core.somaxconn      # Should be 65535
cat /sys/kernel/mm/transparent_hugepage/enabled  # Should be [never]
cpufreq-info | grep "current policy"  # Should be performance
```

---

## 6. Docker Installation & Configuration

### 6.1 Install Docker Engine

```bash
# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Add Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Update package index
sudo apt update

# Install Docker Engine, CLI, and Compose
sudo apt install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin

# Verify installation
docker --version
docker compose version

# Should show:
# Docker version 24.0.x
# Docker Compose version v2.x.x
```

### 6.2 Configure Docker Daemon

```bash
# Create Docker daemon configuration
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json > /dev/null << 'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ],
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 1048576,
      "Soft": 1048576
    },
    "nproc": {
      "Name": "nproc",
      "Hard": 1048576,
      "Soft": 1048576
    }
  },
  "max-concurrent-downloads": 10,
  "max-concurrent-uploads": 10,
  "live-restore": true,
  "userland-proxy": false,
  "experimental": false,
  "metrics-addr": "127.0.0.1:9323",
  "default-address-pools": [
    {
      "base": "172.28.0.0/16",
      "size": 24
    }
  ]
}
EOF

# Restart Docker to apply configuration
sudo systemctl restart docker

# Verify Docker is running
sudo systemctl status docker

# Check Docker info
sudo docker info | grep -A 5 "Storage Driver"
# Should show: overlay2
```

### 6.3 Add User to Docker Group (No sudo needed)

```bash
# Add current user to docker group
sudo usermod -aG docker $USER

# Apply group membership (logout/login or use newgrp)
newgrp docker

# Verify you can run Docker without sudo
docker ps
# Should show empty list (no containers yet)

# If you get permission denied, logout and login again:
# exit
# ssh perf-test
```

### 6.4 Configure Docker Resource Limits

```bash
# Docker on Linux uses host resources directly
# Verify available resources
docker system df
docker system info | grep -i memory
docker system info | grep -i cpu

# Should show:
# Total Memory: ~32GB
# CPUs: 16 (10 cores × hyperthreading)
```

---

## 7. Project Setup

### 7.1 Install Java and Maven

```bash
# Install OpenJDK 17 (required for Spring Boot 2.5.7)
sudo apt install -y openjdk-17-jdk openjdk-17-jre maven

# Verify installation
java -version
# Should show: openjdk version "17.x.x"

javac -version
# Should show: javac 17.x.x

mvn -version
# Should show: Apache Maven 3.6.x or higher
```

### 7.2 Clone Project from GitHub

```bash
# Create workspace directory
mkdir -p ~/workspace
cd ~/workspace

# Clone project (replace with your repo URL if different)
git clone https://github.com/yangjianingpaul/High-Concurrency_E-Commerce_Caching_System.git

# Navigate to project
cd High-Concurrency_E-Commerce_Caching_System

# Verify project structure
ls -la
# Should see: pom.xml, src/, docker-env/, README.md, etc.
```

### 7.3 Build Project

```bash
# Build project (skip tests for now)
mvn clean package -DskipTests

# This will take 5-10 minutes on first build (downloading dependencies)
# Watch for:
# [INFO] BUILD SUCCESS

# Verify JAR file created
ls -lh target/*.jar
# Should show: hm-dianping-0.0.1-SNAPSHOT.jar (~50MB)
```

### 7.4 Transfer Project from MacBook (Alternative)

**If you prefer to transfer from your MacBook instead of cloning:**

**On MacBook:**

```bash
# Navigate to project directory
cd /Users/paulyang/AI/ClaudeProject/High-Concurrency_E-Commerce_Caching_System

# Transfer entire project to Linux PC
scp -r . perf-test:~/workspace/High-Concurrency_E-Commerce_Caching_System/

# This may take 2-5 minutes depending on file size
```

---

## 8. Production Infrastructure Deployment

### 8.1 Create Production Docker Compose Configuration

```bash
# Navigate to docker-env directory
cd ~/workspace/High-Concurrency_E-Commerce_Caching_System/docker-env

# Create production configuration for your hardware
cat > docker-compose.production.yml << 'EOFCONFIG'
version: "3.8"

services:
  # ============================================================
  # MySQL - Primary Database (8GB RAM, 3 cores)
  # ============================================================
  mysql:
    image: mysql:8.0
    container_name: mysql-prod
    hostname: mysql-prod
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: Perf@Test@2025!Secure
      MYSQL_DATABASE: ecommerce
      TZ: Asia/Shanghai
    volumes:
      - ./mysql/conf/my-production.cnf:/etc/mysql/conf.d/my.cnf:ro
      - mysql-data:/var/lib/mysql
      - ./mysql/init:/docker-entrypoint-initdb.d:ro
      - mysql-logs:/var/log/mysql
    command: >
      --default-authentication-plugin=mysql_native_password
      --performance-schema=ON
      --performance-schema-instrument='%=ON'
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '3'
          memory: 8G
        reservations:
          cpus: '2'
          memory: 6G
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-pPerf@Test@2025!Secure"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  # ============================================================
  # Redis Cluster - 6 Nodes (3 masters + 3 replicas)
  # ============================================================
  redis-node-1:
    image: redis:7.2-alpine
    container_name: redis-node-1
    hostname: redis-node-1
    ports:
      - "7000:7000"
      - "17000:17000"
    volumes:
      - ./redis/cluster/node-1:/data
      - ./redis/cluster/redis-cluster.conf:/etc/redis/redis.conf:ro
    command: >
      redis-server /etc/redis/redis.conf
      --port 7000
      --cluster-announce-port 7000
      --cluster-announce-bus-port 17000
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 700M
        reservations:
          memory: 512M
    restart: unless-stopped

  redis-node-2:
    image: redis:7.2-alpine
    container_name: redis-node-2
    hostname: redis-node-2
    ports:
      - "7001:7001"
      - "17001:17001"
    volumes:
      - ./redis/cluster/node-2:/data
      - ./redis/cluster/redis-cluster.conf:/etc/redis/redis.conf:ro
    command: >
      redis-server /etc/redis/redis.conf
      --port 7001
      --cluster-announce-port 7001
      --cluster-announce-bus-port 17001
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 700M
        reservations:
          memory: 512M
    restart: unless-stopped

  redis-node-3:
    image: redis:7.2-alpine
    container_name: redis-node-3
    hostname: redis-node-3
    ports:
      - "7002:7002"
      - "17002:17002"
    volumes:
      - ./redis/cluster/node-3:/data
      - ./redis/cluster/redis-cluster.conf:/etc/redis/redis.conf:ro
    command: >
      redis-server /etc/redis/redis.conf
      --port 7002
      --cluster-announce-port 7002
      --cluster-announce-bus-port 17002
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 700M
        reservations:
          memory: 512M
    restart: unless-stopped

  redis-node-4:
    image: redis:7.2-alpine
    container_name: redis-node-4
    hostname: redis-node-4
    ports:
      - "7003:7003"
      - "17003:17003"
    volumes:
      - ./redis/cluster/node-4:/data
      - ./redis/cluster/redis-cluster.conf:/etc/redis/redis.conf:ro
    command: >
      redis-server /etc/redis/redis.conf
      --port 7003
      --cluster-announce-port 7003
      --cluster-announce-bus-port 17003
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 700M
        reservations:
          memory: 512M
    restart: unless-stopped

  redis-node-5:
    image: redis:7.2-alpine
    container_name: redis-node-5
    hostname: redis-node-5
    ports:
      - "7004:7004"
      - "17004:17004"
    volumes:
      - ./redis/cluster/node-5:/data
      - ./redis/cluster/redis-cluster.conf:/etc/redis/redis.conf:ro
    command: >
      redis-server /etc/redis/redis.conf
      --port 7004
      --cluster-announce-port 7004
      --cluster-announce-bus-port 17004
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 700M
        reservations:
          memory: 512M
    restart: unless-stopped

  redis-node-6:
    image: redis:7.2-alpine
    container_name: redis-node-6
    hostname: redis-node-6
    ports:
      - "7005:7005"
      - "17005:17005"
    volumes:
      - ./redis/cluster/node-6:/data
      - ./redis/cluster/redis-cluster.conf:/etc/redis/redis.conf:ro
    command: >
      redis-server /etc/redis/redis.conf
      --port 7005
      --cluster-announce-port 7005
      --cluster-announce-bus-port 17005
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 700M
        reservations:
          memory: 512M
    restart: unless-stopped

  # Redis Cluster Initialization
  redis-cluster-init:
    image: redis:7.2-alpine
    container_name: redis-cluster-init
    depends_on:
      - redis-node-1
      - redis-node-2
      - redis-node-3
      - redis-node-4
      - redis-node-5
      - redis-node-6
    networks:
      - ecommerce-prod-net
    command: >
      sh -c "
      echo 'Waiting for Redis nodes to be ready...' &&
      sleep 10 &&
      redis-cli --cluster create
      redis-node-1:7000
      redis-node-2:7001
      redis-node-3:7002
      redis-node-4:7003
      redis-node-5:7004
      redis-node-6:7005
      --cluster-replicas 1 --cluster-yes &&
      echo 'Redis Cluster initialized successfully!'
      "
    restart: "no"

  # ============================================================
  # Application Instances (3x for load balancing)
  # ============================================================
  app-1:
    image: openjdk:17-jdk-slim
    container_name: ecommerce-app-1
    hostname: app-1
    ports:
      - "8081:8081"
      - "9091:9091"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-prod:3306/ecommerce?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: Perf@Test@2025!Secure
      SPRING_REDIS_CLUSTER_NODES: redis-node-1:7000,redis-node-2:7001,redis-node-3:7002,redis-node-4:7003,redis-node-5:7004,redis-node-6:7005
      JAVA_OPTS: >-
        -Xms2g
        -Xmx4g
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
        -XX:+HeapDumpOnOutOfMemoryError
        -XX:HeapDumpPath=/tmp/heapdump-app1.hprof
        -XX:+ExitOnOutOfMemoryError
        -Dcom.sun.management.jmxremote=true
        -Dcom.sun.management.jmxremote.port=9091
        -Dcom.sun.management.jmxremote.authenticate=false
        -Dcom.sun.management.jmxremote.ssl=false
        -Djava.rmi.server.hostname=localhost
    volumes:
      - ../target/hm-dianping-0.0.1-SNAPSHOT.jar:/app/app.jar:ro
      - app-1-logs:/var/log/app
    command: sh -c "java $${JAVA_OPTS} -jar /app/app.jar"
    networks:
      - ecommerce-prod-net
    depends_on:
      mysql:
        condition: service_healthy
      redis-cluster-init:
        condition: service_completed_successfully
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 4500M
        reservations:
          cpus: '1.5'
          memory: 3G
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    restart: unless-stopped

  app-2:
    image: openjdk:17-jdk-slim
    container_name: ecommerce-app-2
    hostname: app-2
    ports:
      - "8082:8081"
      - "9092:9092"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-prod:3306/ecommerce?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: Perf@Test@2025!Secure
      SPRING_REDIS_CLUSTER_NODES: redis-node-1:7000,redis-node-2:7001,redis-node-3:7002,redis-node-4:7003,redis-node-5:7004,redis-node-6:7005
      JAVA_OPTS: >-
        -Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
        -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump-app2.hprof
        -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=9092
        -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
    volumes:
      - ../target/hm-dianping-0.0.1-SNAPSHOT.jar:/app/app.jar:ro
      - app-2-logs:/var/log/app
    command: sh -c "java $${JAVA_OPTS} -jar /app/app.jar"
    networks:
      - ecommerce-prod-net
    depends_on:
      mysql:
        condition: service_healthy
      redis-cluster-init:
        condition: service_completed_successfully
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 4500M
        reservations:
          cpus: '1.5'
          memory: 3G
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    restart: unless-stopped

  app-3:
    image: openjdk:17-jdk-slim
    container_name: ecommerce-app-3
    hostname: app-3
    ports:
      - "8083:8081"
      - "9093:9093"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-prod:3306/ecommerce?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: Perf@Test@2025!Secure
      SPRING_REDIS_CLUSTER_NODES: redis-node-1:7000,redis-node-2:7001,redis-node-3:7002,redis-node-4:7003,redis-node-5:7004,redis-node-6:7005
      JAVA_OPTS: >-
        -Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
        -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump-app3.hprof
        -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=9093
        -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
    volumes:
      - ../target/hm-dianping-0.0.1-SNAPSHOT.jar:/app/app.jar:ro
      - app-3-logs:/var/log/app
    command: sh -c "java $${JAVA_OPTS} -jar /app/app.jar"
    networks:
      - ecommerce-prod-net
    depends_on:
      mysql:
        condition: service_healthy
      redis-cluster-init:
        condition: service_completed_successfully
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 4500M
        reservations:
          cpus: '1.5'
          memory: 3G
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    restart: unless-stopped

  # ============================================================
  # Nginx Load Balancer
  # ============================================================
  nginx:
    image: nginx:1.25-alpine
    container_name: nginx-lb
    hostname: nginx-lb
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx-production.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/html:/usr/share/nginx/html:ro
      - nginx-logs:/var/log/nginx
    networks:
      - ecommerce-prod-net
    depends_on:
      app-1:
        condition: service_healthy
      app-2:
        condition: service_healthy
      app-3:
        condition: service_healthy
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          memory: 256M
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    restart: unless-stopped

  # ============================================================
  # Monitoring Stack
  # ============================================================
  prometheus:
    image: prom/prometheus:v2.48.0
    container_name: prometheus-prod
    hostname: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus-production.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=7d'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
      - '--web.enable-lifecycle'
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 1G
        reservations:
          memory: 512M
    restart: unless-stopped

  grafana:
    image: grafana/grafana:10.2.2
    container_name: grafana-prod
    hostname: grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: Perf@Admin@2025!
      GF_USERS_ALLOW_SIGN_UP: "false"
      GF_SERVER_ROOT_URL: http://localhost:3000
      GF_INSTALL_PLUGINS: redis-datasource
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    networks:
      - ecommerce-prod-net
    depends_on:
      - prometheus
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 1G
        reservations:
          memory: 512M
    restart: unless-stopped

  # ============================================================
  # Metrics Exporters
  # ============================================================
  redis-exporter:
    image: oliver006/redis_exporter:v1.56.0-alpine
    container_name: redis-exporter
    hostname: redis-exporter
    ports:
      - "9121:9121"
    environment:
      REDIS_ADDR: redis://redis-node-1:7000,redis://redis-node-2:7001,redis://redis-node-3:7002
    networks:
      - ecommerce-prod-net
    depends_on:
      - redis-cluster-init
    deploy:
      resources:
        limits:
          cpus: '0.2'
          memory: 256M
    restart: unless-stopped

  mysql-exporter:
    image: prom/mysqld-exporter:v0.15.1
    container_name: mysql-exporter
    hostname: mysql-exporter
    ports:
      - "9104:9104"
    environment:
      DATA_SOURCE_NAME: "root:Perf@Test@2025!Secure@(mysql-prod:3306)/"
    networks:
      - ecommerce-prod-net
    depends_on:
      mysql:
        condition: service_healthy
    deploy:
      resources:
        limits:
          cpus: '0.2'
          memory: 256M
    restart: unless-stopped

  node-exporter:
    image: prom/node-exporter:v1.7.0
    container_name: node-exporter
    hostname: node-exporter
    ports:
      - "9100:9100"
    command:
      - '--path.procfs=/host/proc'
      - '--path.sysfs=/host/sys'
      - '--path.rootfs=/rootfs'
      - '--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)'
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    networks:
      - ecommerce-prod-net
    deploy:
      resources:
        limits:
          cpus: '0.2'
          memory: 256M
    restart: unless-stopped

# ============================================================
# Networks
# ============================================================
networks:
  ecommerce-prod-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16
          gateway: 172.28.0.1

# ============================================================
# Volumes
# ============================================================
volumes:
  mysql-data:
    driver: local
  mysql-logs:
    driver: local
  app-1-logs:
    driver: local
  app-2-logs:
    driver: local
  app-3-logs:
    driver: local
  nginx-logs:
    driver: local
  prometheus-data:
    driver: local
  grafana-data:
    driver: local
EOFCONFIG

echo "✓ docker-compose.production.yml created"
```

### 8.2 Create Required Directories

```bash
# Create all necessary directories
mkdir -p mysql/conf mysql/init
mkdir -p redis/cluster/{node-1,node-2,node-3,node-4,node-5,node-6}
mkdir -p nginx
mkdir -p monitoring/prometheus monitoring/grafana/{dashboards,datasources}

# Set proper permissions
chmod -R 755 redis/cluster/
chmod -R 755 mysql/
chmod -R 755 monitoring/

echo "✓ Directory structure created"
```

---

## 9. MySQL Optimization

### 9.1 Create Optimized MySQL Configuration

```bash
# Create production MySQL configuration for 8GB allocation
cat > mysql/conf/my-production.cnf << 'EOFMYSQL'
[mysqld]
# ============================================================
# Server Identity
# ============================================================
server-id = 1
port = 3306

# ============================================================
# Character Set & Collation
# ============================================================
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# ============================================================
# Connection Settings (Optimized for 12600KF)
# ============================================================
max_connections = 500
max_connect_errors = 100000
max_allowed_packet = 256M
interactive_timeout = 28800
wait_timeout = 28800

# ============================================================
# InnoDB Buffer Pool (5.5GB for 8GB allocation)
# ============================================================
innodb_buffer_pool_size = 5632M
innodb_buffer_pool_instances = 8
innodb_buffer_pool_chunk_size = 128M

# ============================================================
# InnoDB Log Files (Optimized for NVMe)
# ============================================================
innodb_log_file_size = 512M
innodb_log_buffer_size = 64M
innodb_log_files_in_group = 2

# ============================================================
# InnoDB Flush Behavior (Optimized for Performance)
# ============================================================
innodb_flush_log_at_trx_commit = 2
innodb_flush_method = O_DIRECT
innodb_flush_neighbors = 0

# ============================================================
# InnoDB I/O Settings (Optimized for NVMe + 12600KF)
# ============================================================
innodb_read_io_threads = 8
innodb_write_io_threads = 8
innodb_io_capacity = 4000
innodb_io_capacity_max = 8000
innodb_lru_scan_depth = 2000

# ============================================================
# InnoDB Performance Tuning
# ============================================================
innodb_adaptive_hash_index = ON
innodb_change_buffering = all
innodb_change_buffer_max_size = 25
innodb_doublewrite = ON
innodb_file_per_table = ON
innodb_flush_sync = ON
innodb_old_blocks_time = 1000
innodb_open_files = 4000
innodb_page_cleaners = 8
innodb_purge_threads = 4
innodb_stats_on_metadata = OFF

# ============================================================
# Query Cache (Disabled in MySQL 8.0)
# ============================================================
# query_cache_type = 0
# query_cache_size = 0

# ============================================================
# Temp Tables
# ============================================================
tmp_table_size = 512M
max_heap_table_size = 512M
tmpdir = /tmp

# ============================================================
# Thread Settings
# ============================================================
thread_cache_size = 100
thread_stack = 512K

# ============================================================
# Table Cache
# ============================================================
table_open_cache = 4000
table_definition_cache = 2000
table_open_cache_instances = 16

# ============================================================
# Sort & Join Buffers
# ============================================================
sort_buffer_size = 4M
join_buffer_size = 4M
read_buffer_size = 2M
read_rnd_buffer_size = 8M

# ============================================================
# Binary Logging (Disabled for performance testing)
# ============================================================
disable_log_bin

# ============================================================
# Slow Query Log
# ============================================================
slow_query_log = ON
slow_query_log_file = /var/log/mysql/slow-query.log
long_query_time = 1
log_queries_not_using_indexes = ON
log_throttle_queries_not_using_indexes = 10

# ============================================================
# General Log (Disabled for performance)
# ============================================================
general_log = OFF

# ============================================================
# Performance Schema (Enabled for monitoring)
# ============================================================
performance_schema = ON
performance-schema-instrument = '%=ON'
performance-schema-consumer-events-statements-current = ON
performance-schema-consumer-events-statements-history = ON
performance-schema-consumer-events-statements-history-long = ON
performance_schema_max_table_instances = 12500
performance_schema_max_table_handles = 4000

# ============================================================
# Security
# ============================================================
skip-name-resolve
local_infile = OFF

# ============================================================
# MyISAM Settings (Legacy, but still used by some system tables)
# ============================================================
key_buffer_size = 64M
myisam_sort_buffer_size = 128M

[client]
port = 3306
socket = /var/run/mysqld/mysqld.sock
default-character-set = utf8mb4

[mysql]
default-character-set = utf8mb4
no-auto-rehash

[mysqldump]
quick
quote-names
max_allowed_packet = 256M
default-character-set = utf8mb4
EOFMYSQL

echo "✓ MySQL production configuration created"
```

### 9.2 Create Database Initialization Script

```bash
# Create database init script
cat > mysql/init/01-init-ecommerce.sql << 'EOFSQL'
-- ============================================================
-- E-Commerce Database Initialization
-- ============================================================

USE ecommerce;

-- Set timezone
SET time_zone = '+08:00';

-- Create tables (if not exists from schema)
-- Note: Actual schema will be loaded from your project's SQL files

-- Sample performance tuning indexes
-- These should be adjusted based on your actual schema

-- Example: Ensure indexes exist on frequently queried columns
-- ALTER TABLE tb_user ADD INDEX idx_phone (phone);
-- ALTER TABLE tb_shop ADD INDEX idx_type_id (type_id);
-- ALTER TABLE tb_voucher_order ADD INDEX idx_user_voucher (user_id, voucher_id);

-- Insert initial data if needed
-- INSERT INTO ...

SELECT 'Database initialized successfully' AS status;
EOFSQL

echo "✓ MySQL initialization script created"
```

---

## 10. Redis Cluster Optimization

### 10.1 Create Redis Cluster Configuration

```bash
# Create optimized Redis cluster configuration
cat > redis/cluster/redis-cluster.conf << 'EOFREDIS'
# ============================================================
# Redis Cluster Configuration (Optimized for 12600KF + NVMe)
# ============================================================

# Cluster Mode
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 15000
cluster-replica-validity-factor 10
cluster-migration-barrier 1
cluster-require-full-coverage yes

# Network
bind 0.0.0.0
protected-mode no
port 7000
tcp-backlog 511
timeout 0
tcp-keepalive 300

# Memory Management (600MB per node for 6 nodes = ~3.6GB total)
maxmemory 600mb
maxmemory-policy allkeys-lru
maxmemory-samples 5

# Lazy Freeing
lazyfree-lazy-eviction yes
lazyfree-lazy-expire yes
lazyfree-lazy-server-del yes
replica-lazy-flush yes

# Append Only File (AOF)
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec
no-appendfsync-on-rewrite no
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# RDB Snapshots
save 900 1
save 300 10
save 60 10000
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
dbfilename dump.rdb
dir /data

# Replication
repl-diskless-sync yes
repl-diskless-sync-delay 5
repl-diskless-load on-empty-db
repl-disable-tcp-nodelay no

# Performance Tuning
# Multi-threaded I/O (Redis 6.0+)
io-threads 4
io-threads-do-reads yes

# Active defragmentation
activedefrag yes
active-defrag-ignore-bytes 100mb
active-defrag-threshold-lower 10
active-defrag-threshold-upper 100
active-defrag-cycle-min 5
active-defrag-cycle-max 75

# Logging
loglevel notice
logfile ""

# Databases
databases 16

# Lua Scripting
lua-time-limit 5000

# Slow Log
slowlog-log-slower-than 10000
slowlog-max-len 128

# Latency Monitoring
latency-monitor-threshold 100

# Event Notification
notify-keyspace-events ""

# Advanced
hash-max-ziplist-entries 512
hash-max-ziplist-value 64
list-max-ziplist-size -2
list-compress-depth 0
set-max-intset-entries 512
zset-max-ziplist-entries 128
zset-max-ziplist-value 64
hll-sparse-max-bytes 3000
stream-node-max-bytes 4096
stream-node-max-entries 100
activerehashing yes
client-output-buffer-limit normal 0 0 0
client-output-buffer-limit replica 256mb 64mb 60
client-output-buffer-limit pubsub 32mb 8mb 60
hz 10
dynamic-hz yes
aof-rewrite-incremental-fsync yes
rdb-save-incremental-fsync yes
EOFREDIS

echo "✓ Redis cluster configuration created"
```

---

## 11. Monitoring Setup

### 11.1 Create Prometheus Configuration

```bash
# Create Prometheus configuration
cat > monitoring/prometheus-production.yml << 'EOFPROM'
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'perf-test-12600kf'
    environment: 'production-test'

scrape_configs:
  # Prometheus itself
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # Spring Boot Applications
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'app-1:8081'
          - 'app-2:8081'
          - 'app-3:8081'
        labels:
          service: 'ecommerce-app'
          cluster: 'app-cluster'

  # Redis Cluster
  - job_name: 'redis-cluster'
    static_configs:
      - targets: ['redis-exporter:9121']
        labels:
          service: 'redis-cluster'

  # MySQL Database
  - job_name: 'mysql'
    static_configs:
      - targets: ['mysql-exporter:9104']
        labels:
          service: 'mysql-primary'

  # System Metrics
  - job_name: 'node-exporter'
    static_configs:
      - targets: ['node-exporter:9100']
        labels:
          service: 'linux-host'
          hostname: 'perf-test-server'
EOFPROM

echo "✓ Prometheus configuration created"
```

### 11.2 Create Grafana Data Source

```bash
# Create Grafana datasource configuration
mkdir -p monitoring/grafana/datasources
cat > monitoring/grafana/datasources/prometheus.yml << 'EOFGRAF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: 15s
EOFGRAF

echo "✓ Grafana datasource configuration created"
```

### 11.3 Create Nginx Configuration

```bash
# Create Nginx load balancer configuration
cat > nginx/nginx-production.conf << 'EOFNGINX'
user nginx;
worker_processes auto;
worker_cpu_affinity auto;
worker_rlimit_nofile 100000;

error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    use epoll;
    worker_connections 10000;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for" '
                    'rt=$request_time uct="$upstream_connect_time" '
                    'uht="$upstream_header_time" urt="$upstream_response_time"';

    access_log /var/log/nginx/access.log main;

    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    keepalive_requests 10000;

    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    upstream backend {
        least_conn;
        server app-1:8081 max_fails=3 fail_timeout=30s weight=1;
        server app-2:8081 max_fails=3 fail_timeout=30s weight=1;
        server app-3:8081 max_fails=3 fail_timeout=30s weight=1;
        keepalive 100;
        keepalive_requests 1000;
        keepalive_timeout 60s;
    }

    server {
        listen 80;
        server_name _;

        location /health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }

        location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff|woff2|ttf|eot)$ {
            root /usr/share/nginx/html;
            expires 1d;
            add_header Cache-Control "public, immutable";
        }

        location / {
            proxy_pass http://backend;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            proxy_connect_timeout 10s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;

            proxy_buffering on;
            proxy_buffer_size 8k;
            proxy_buffers 16 8k;
            proxy_busy_buffers_size 16k;

            proxy_next_upstream error timeout invalid_header http_500 http_502 http_503;
            proxy_next_upstream_tries 2;
        }
    }

    server {
        listen 8080;
        location /nginx_status {
            stub_status on;
            access_log off;
            allow 172.28.0.0/16;
            deny all;
        }
    }
}
EOFNGINX

echo "✓ Nginx configuration created"
```

---

## 12. Verification & Testing

### 12.1 Deploy Infrastructure

```bash
# Navigate to docker-env directory
cd ~/workspace/High-Concurrency_E-Commerce_Caching_System/docker-env

# Pull all Docker images first (to save time)
docker compose -f docker-compose.production.yml pull

# Start all services
docker compose -f docker-compose.production.yml up -d

# Wait for services to start (3-5 minutes)
echo "Waiting for services to start..."
sleep 180

# Check all containers are running
docker compose -f docker-compose.production.yml ps

# Should show all services as "Up" and healthy
```

### 12.2 Verify Services

```bash
# Check MySQL
docker exec -it mysql-prod mysql -uroot -p'Perf@Test@2025!Secure' -e "SELECT VERSION();"
# Should show: MySQL 8.0.x

# Check Redis Cluster
docker exec -it redis-node-1 redis-cli -p 7000 cluster info
# Should show: cluster_state:ok

docker exec -it redis-node-1 redis-cli -p 7000 cluster nodes
# Should show 6 nodes (3 masters, 3 replicas)

# Check Application instances
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
# All should return: {"status":"UP"}

# Check Nginx load balancer
curl http://localhost/health
# Should return: healthy

# Check Prometheus
curl http://localhost:9090/-/healthy
# Should return: Prometheus is Healthy.

# Check Grafana (from MacBook browser)
# Open: http://192.168.1.100:3000
# Login: admin / Perf@Admin@2025!
```

### 12.3 Performance Verification

```bash
# Check resource usage
docker stats --no-stream

# Should show approximate usage:
# mysql-prod:     2-3GB RAM, 50-100% CPU (during startup)
# app-1/2/3:      2-3GB RAM each, 10-30% CPU
# redis-node-*:   300-500MB RAM each
# Total:          ~20-25GB RAM used
```

### 12.4 Initialize Redis Stream

```bash
# Create Redis Stream consumer group for order processing
docker exec -it redis-node-1 redis-cli -p 7000 \
  XGROUP CREATE stream.orders g1 0 MKSTREAM

# Verify
docker exec -it redis-node-1 redis-cli -p 7000 \
  XINFO GROUPS stream.orders

# Should show group g1
```

---

## 13. Troubleshooting

### Common Issues and Solutions

#### Issue 1: MySQL Container Won't Start

```bash
# Check logs
docker logs mysql-prod

# Common fixes:
# 1. Permission issues
sudo chown -R 999:999 mysql/

# 2. Port already in use
sudo lsof -i :3306
sudo kill -9 <PID>

# 3. Configuration errors
docker exec -it mysql-prod cat /etc/mysql/conf.d/my.cnf
```

#### Issue 2: Redis Cluster Initialization Failed

```bash
# Check Redis logs
docker logs redis-node-1

# Manual cluster creation
docker exec -it redis-node-1 redis-cli --cluster create \
  172.28.0.11:7000 172.28.0.12:7001 172.28.0.13:7002 \
  172.28.0.14:7003 172.28.0.15:7004 172.28.0.16:7005 \
  --cluster-replicas 1 --cluster-yes

# Replace IPs with actual container IPs:
docker inspect redis-node-1 | grep IPAddress
```

#### Issue 3: Application Won't Start

```bash
# Check logs
docker logs ecommerce-app-1

# Common issues:
# 1. JAR file not found
ls -la ../target/*.jar

# 2. Database connection failed
docker exec -it mysql-prod mysql -uroot -p'Perf@Test@2025!Secure' -e "SHOW DATABASES;"

# 3. Memory issues
docker stats ecommerce-app-1
```

#### Issue 4: Cannot Access from MacBook

```bash
# On Linux PC, check firewall
sudo ufw status

# Ensure ports are open
sudo ufw allow 3000/tcp  # Grafana
sudo ufw allow 9090/tcp  # Prometheus
sudo ufw allow 80/tcp    # Nginx

# Check if services are listening
sudo netstat -tlnp | grep -E ':(80|3000|9090)'
```

---

## Summary Checklist

### ✅ Completion Checklist

- [ ] Ubuntu Server 22.04 installed on NVMe
- [ ] System updated and essential tools installed
- [ ] SSH access from MacBook working
- [ ] System performance tuning applied
- [ ] Docker installed and configured
- [ ] Project built successfully
- [ ] MySQL optimized configuration created
- [ ] Redis cluster configuration created
- [ ] All services deployed via Docker Compose
- [ ] All containers running and healthy
- [ ] MySQL accessible and initialized
- [ ] Redis cluster formed (6 nodes)
- [ ] Applications responding to health checks
- [ ] Nginx load balancer working
- [ ] Monitoring stack (Prometheus + Grafana) accessible
- [ ] Redis Stream consumer group created

### 📊 Expected Resource Usage

| Component | CPU Cores | RAM | Storage |
|-----------|-----------|-----|---------|
| MySQL | 2-3 cores | 6-8GB | ~50GB |
| Redis Cluster (6 nodes) | 2-3 cores | 3-4GB | ~10GB |
| Applications (3 instances) | 4-6 cores | 9-12GB | ~2GB |
| Nginx | 0.5 core | 512MB | ~100MB |
| Monitoring | 0.5 core | 1.5GB | ~5GB |
| **Total** | **~12 cores** | **~28GB** | **~70GB** |
| **Available** | 16 threads | 32GB | 1TB |
| **Headroom** | 25% | 12% | 93% |

### 🎯 Next Steps

1. **Generate Test Data** (see separate guide)
2. **Run Performance Tests** (see PERFORMANCE_TEST_PC_LINUX.md)
3. **Generate Performance Evidence** (see PERFORMANCE_TEST_PC_LINUX.md section 6)
4. **Calculate Hardware Scaling** (see PERFORMANCE_TEST_PC_LINUX.md section 7)

---

## Quick Reference Commands

### SSH from MacBook
```bash
ssh perf-test
# Or: ssh testuser@192.168.1.100
```

### Start/Stop Infrastructure
```bash
cd ~/workspace/High-Concurrency_E-Commerce_Caching_System/docker-env
docker compose -f docker-compose.production.yml up -d    # Start
docker compose -f docker-compose.production.yml down     # Stop
docker compose -f docker-compose.production.yml ps       # Status
docker compose -f docker-compose.production.yml logs -f  # Logs
```

### Monitor Resources
```bash
htop                    # Interactive process viewer
docker stats            # Container resource usage
df -h                   # Disk usage
free -h                 # Memory usage
```

### Access Services (from MacBook browser)
```
Grafana:     http://192.168.1.100:3000  (admin / Perf@Admin@2025!)
Prometheus:  http://192.168.1.100:9090
Application: http://192.168.1.100:80
```

---

**Setup Complete! Your production-grade performance testing environment is ready!** 🎉
