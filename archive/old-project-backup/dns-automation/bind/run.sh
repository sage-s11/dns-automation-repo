#!/bin/bash

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Create directories
mkdir -p "$BASE_DIR/cache/bind"
mkdir -p "$BASE_DIR/run/named"
mkdir -p "$BASE_DIR/logs/bind"

# Get network IP
NETWORK_IP=$(ip route get 1.1.1.1 | awk '{print $7; exit}')

echo "🌐 Starting BIND9 DNS Server..."
echo "   Network IP: $NETWORK_IP"
echo "   DNS Port: 1053"
echo ""

# Remove old container if exists
podman rm -f bind9-demo 2>/dev/null

# Start with network binding
podman run -d \
  --name bind9-demo \
  -p 0.0.0.0:1053:53/udp \
  -p 0.0.0.0:1053:53/tcp \
  -p 127.0.0.1:1953:1953/tcp \
  -v "$BASE_DIR/config/named.conf":/etc/bind/named.conf:Z \
  -v "$BASE_DIR/config/rndc/rndc.key":/etc/bind/rndc.key:Z \
  -v "$BASE_DIR/zones":/zones:Z \
  -v "$BASE_DIR/cache/bind":/var/cache/bind:Z \
  -v "$BASE_DIR/run/named":/run/named:Z \
  -v "$BASE_DIR/logs/bind":/var/log/bind:Z \
  docker.io/internetsystemsconsortium/bind9:9.18

sleep 3

echo "✅ DNS Server running!"
echo ""
echo "📱 Test from this computer:"
echo "   dig @127.0.0.1 -p 1053 mail.examplenv.demo +short"
echo ""
echo "📱 Test from phone/other devices:"
echo "   dig @$NETWORK_IP -p 1053 mail.examplenv.demo +short"
echo ""
echo "💡 On Android: Install 'DNS Lookup' app"
echo "💡 On iPhone: Install 'Network Analyzer' app"
echo ""
