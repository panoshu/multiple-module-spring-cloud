#!/bin/bash
# 全链路集成测试：按依赖顺序启动 7 个业务服务 + integration，端口就绪后继续
cd /home/archie/develop/java/multiple-module-spring-cloud

# VirtualThreadExecutor（StructuredTaskScope）依赖 JDK 25 preview，缺 --enable-preview 时
# 首次异步派发/事件投递会抛 UnsupportedClassVersionError（class file version 69.65535）。
JVM="-Xms128m -Xmx256m -XX:+UseG1GC --enable-preview"

start() {
  local name=$1 jar=$2; shift 2
  echo "[$(date +%T)] starting $name ..."
  java $JVM -jar "$jar" "$@" > /tmp/svc-$name.log 2>&1 &
  echo $! > /tmp/svc-$name.pid
}

wait_port() {
  local port=$1 name=$2
  for i in $(seq 1 60); do
    if (echo > /dev/tcp/127.0.0.1/$port) 2>/dev/null; then
      echo "[$(date +%T)] $name UP (port $port)"; return 0
    fi
    if ! kill -0 "$(cat /tmp/svc-$name.pid)" 2>/dev/null; then
      echo "[$(date +%T)] $name PROCESS DIED (port $port)"; grep -E "Caused by|APPLICATION FAILED" /tmp/svc-$name.log | head -3; return 1
    fi
    sleep 2
  done
  echo "[$(date +%T)] $name TIMEOUT (port $port)"; tail -5 /tmp/svc-$name.log; return 1
}

start auth      auth-service/auth-starter/target/auth-service.jar
wait_port 18085 auth || exit 1
start file      file-service/file-starter/target/file-service.jar --spring.profiles.active=local --permission.enforcement.enabled=false
wait_port 18082 file || exit 1
start approval  approval-service/approval-starter/target/approval-service.jar --spring.profiles.active=local --permission.enforcement.enabled=false
wait_port 18081 approval || exit 1
start annuity   annuity-service/annuity-starter/target/annuity-service.jar --spring.profiles.active=local --permission.enforcement.enabled=false
wait_port 18090 annuity || exit 1
start integration integration-service/integration-service-starter/target/integration-service.jar --spring.profiles.active=local --permission.enforcement.enabled=false
wait_port 18083 integration || exit 1
start intranet-bff intranet-bff/intranet-bff-starter/target/intranet-bff.jar --spring.profiles.active=local --permission.enforcement.enabled=false
wait_port 18091 intranet-bff || exit 1
start internet-bff internet-bff/internet-bff-starter/target/internet-bff.jar --spring.profiles.active=local --permission.enforcement.enabled=false
wait_port 18092 internet-bff || exit 1
start gateway   channel-gateway/target/channel-gateway.jar --spring.profiles.active=dev
wait_port 18080 gateway || exit 1

echo "[$(date +%T)] ALL 8 SERVICES UP"
wait
