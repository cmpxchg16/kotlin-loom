# Kotlin Loom

This project is an experiment project on how to use the [Kotlin Programming Language](https://kotlinlang.org/) with the most exciting project in the JVM for the last decade [OpenJDK Project Loom](https://openjdk.java.net/projects/loom/) (IMHO). That project demonstrate 
how to use the new concurrency model [Virtual Threads](https://openjdk.java.net/projects/loom/), that new concurrency model brings the ability to open millions of virtual threads (generally it's not limited to millions -- it's matter of HW resources) on a single instance while using the intuitive synchronous model, which makes the coding much more logically and easy to understand than the reactive/asynchronous model which is complex. The main use-case for such model, is a highly concurrent client/server, like a websocket server, or client web scraper.     

**Disclaimer**: Before you continue, this code is not intended to be a professional code, nor the best/accurate benchmark, it's totally for fun `¯\_(ツ)_/¯`  

To use that project you will need to use [Project Loom Early-Access Builds](https://jdk.java.net/loom/), that early access going to be released in the next [JDK version 19](https://openjdk.java.net/projects/jdk/19/).     

The project has two main classes -- [DummyHttpClient](src/main/java/kotlinx/loom/client/DummyHttpClient.kt) & [DummyHttpServer](src/main/java/kotlinx/loom/server/DummyHttpServer.kt), the dummy HTTP client knows to interact with the dummy HTTP server with the new concurrency model of virtual threads. To bypass the source ip + source port TCP 65k limit, both the client and the server using multiple ports.  

**NOTE:** You can use the builtin client for the server benchmark experiment, however, if you prefer to use standard benchmark tool, you can use the lead HTTP benchmarking tool [wrk](https://github.com/wg/wrk), here is how to build it:

```
sudo apt-get install build-essential libssl-dev zip git -y
git clone https://github.com/wg/wrk.git
cd wrk
sudo make
sudo cp wrk /usr/local/bin
```

[Basic Usage](https://github.com/wg/wrk#basic-usage)
```
wrk -t12 -c400 -d30s http://127.0.0.1:8080
```

# Getting started

```
git clone https://github.com/cmpxchg16/kotlin-loom
cd kotlin-loom
mvn clean install
```

Run the server:   

`java -jar target/kotlinx-loom-client.jar-jar-with-dependencies.jar`

Usage: `java -jar target/kotlinx-loom-client.jar-jar-with-dependencies.jar --help`    

Run the client:

`java -jar target/kotlinx-loom-server.jar-jar-with-dependencies.jar`

Usage: `java -jar target/kotlinx-loom-server.jar-jar-with-dependencies.jar --help`

# Linux Kernel Configuration 

The network stack of the OS need to be configured to allow benchmark experiment with such high scalability numbers, here are the settings that need to be configured on a Linux system:

`sudo vim /etc/sysctl.conf`:

```
fs.file-max = 33554432
fs.nr_open = 33554432
net.core.netdev_max_backlog = 400000
net.core.optmem_max = 10000000
net.core.rmem_default = 10000000
net.core.rmem_max = 10000000
net.core.somaxconn = 100000
net.core.wmem_default = 10000000
net.core.wmem_max = 10000000
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_congestion_control = bic
net.ipv4.tcp_ecn = 0
net.ipv4.tcp_max_syn_backlog = 12000
net.ipv4.tcp_max_tw_buckets = 2000000
net.ipv4.tcp_mem = 30000000 30000000 30000000
net.ipv4.tcp_rmem = 30000000 30000000 30000000
net.ipv4.tcp_sack = 1
net.ipv4.tcp_syncookies = 0
net.ipv4.tcp_timestamps = 1
net.ipv4.tcp_wmem = 30000000 30000000 30000000    
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 1
```

Reload sysctl changes: `sudo sysctl -p /etc/sysctl.conf`

`sudo vim /etc/security/limits.conf`:
```
* soft nofile 33554432
* hard nofile 33554432
```

**NOTE:** To reload the limits for permanent change, you will need to reboot. If you don't want to reboot, and you want to do that change just for the current shell, you can do:
```
sudo su
ulimit -Hn 33554432
ulimit -Sn 33554432
```

# Test

Download [openjdk-19-loom+6-625_linux-x64_bin.tar.gz](https://download.java.net/java/early_access/loom/6/openjdk-19-loom+6-625_linux-x64_bin.tar.gz) for the Linux machine:

```
wget https://download.java.net/java/early_access/loom/6/openjdk-19-loom+6-625_linux-x64_bin.tar.gz
tar -xvf openjdk-19-loom+6-625_linux-x64_bin.tar.gz
```

Run server: 
```
./jdk-19/bin/java -jar kotlinx-loom-server.jar-jar-with-dependencies.jar -h 0.0.0.0 -p 8080 -n 200
```

Run client:
```
./jdk-19/bin/java -jar kotlinx-loom-client.jar-jar-with-dependencies.jar -h 35.87.233.194 -p 8080 -c 50000 -n 200
```
The experiment runs on multiple AWS EC2 instance types (both client & server):
* C4 (c4.8xlarge 36vCPU 60GB RAM)
* C5 (c5d.metal 96vCPU 192GB RAM)
* C6i (c6i.metal 128vCPU 256GB RAM)

Server output example for one of the tests:
```
Stats: #Connections: 6567024, #Requests: 9680555686, #Errors: 0
Stats: #Connections: 6567024, #Requests: 9681392764, #Errors: 0
Stats: #Connections: 6567024, #Requests: 9682597421, #Errors: 0
```

Without a surprise, on a stronger instance type, the requests throughput are better, and the load on the machine is much lower. On the strongest instance type `c6i.metal` here is the `top` while the server serving ~ 6.5M+ concurrent clients: 

```
top - 09:37:43 up  1:56,  3 users,  load average: 7.81, 10.11, 11.04
Tasks: 1186 total,   1 running, 1185 sleeping,   0 stopped,   0 zombie
%Cpu(s):  2.3 us,  3.6 sy,  0.0 ni, 92.2 id,  0.0 wa,  0.0 hi,  1.8 si,  0.0 st
MiB Mem : 257746.2 total, 194139.1 free,  50537.4 used,  13069.6 buff/cache
MiB Swap:      0.0 total,      0.0 free,      0.0 used. 205387.0 avail Mem

    PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
   3629 root      20   0   49.1g  26.9g  27956 S 796.4  10.7 720:46.44 java
```

And the `uptime`:
```
09:39:50 up  1:59,  3 users,  load average: 6.75, 9.15, 10.58
```

~ 6.5M+ concurrent virtual threads with ~ 9.5B+ requests is impressive (+ zero tweaking) ! -- well done for the creators & the team of the [Project Loom](https://openjdk.java.net/projects/loom/) :rocket: :clap:

# Copyright
Copyright (c) 2022 Uri Shamay [cmpxchg16.me](https://cmpxchg16.me). See [LICENSE](LICENSE) for further details.