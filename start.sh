#!/bin/bash

# Function to cleanup before script exit
cleanup() {
    echo "Removing node from list..."
    curl -s -d "action=remove&id=$current_id" -X POST https://www.zipp.org/manage_node.php
}

# Initialize IPFS if not already done
if [ ! -d "/root/.ipfs" ]; then
    ipfs init
    cp /usr/app/swarm.key /root/.ipfs/swarm.key
    
    # Modify the IPFS configuration to set specific swarm addresses
    jq '.Addresses.Swarm = ["/ip4/0.0.0.0/tcp/4001", "/ip6/::/tcp/4001"]' /root/.ipfs/config > /tmp/config && mv /tmp/config /root/.ipfs/config
    
    # Set the API to listen on all interfaces
    jq '.Addresses.API = "/ip4/0.0.0.0/tcp/5001"' /root/.ipfs/config > /tmp/config && mv /tmp/config /root/.ipfs/config
    
    # Set the Gateway to listen on all interfaces and port 8080
    jq '.Addresses.Gateway = "/ip4/0.0.0.0/tcp/8080"' /root/.ipfs/config > /tmp/config && mv /tmp/config /root/.ipfs/config
else
    echo "IPFS already initialized. Skipping initialization."
fi

# Import the WebUI CAR file if not already imported
if [ ! -f "/root/.ipfs/webui_imported" ]; then
    ipfs dag import /usr/app/webui.car
    touch /root/.ipfs/webui_imported
else
    echo "WebUI already imported. Skipping import."
fi

# Configure IPFS
ipfs config --json Experimental.FilestoreEnabled true
ipfs config --json Experimental.UrlstoreEnabled true
ipfs config --json Experimental.Libp2pStreamMounting true
ipfs config --json API.HTTPHeaders.Access-Control-Allow-Origin '["*"]'
ipfs config --json API.HTTPHeaders.Access-Control-Allow-Methods '["PUT", "POST", "GET", "DELETE"]'

# Start the IPFS daemon in the background
ipfs bootstrap rm --all
ipfs daemon &

# Wait for IPFS to be ready
while ! curl -s http://127.0.0.1:5001/api/v0/version; do
    echo "Waiting for IPFS to be ready..."
    sleep 1
done

# Fetch and display the current IPFS node ID
current_id=$(ipfs id -f="<id>\n")
echo "Current IPFS Node ID: $current_id"

# Add current node to the list
curl -s -d "action=add&id=$current_id" -X POST https://www.zipp.org/manage_node.php

# Download the list of nodes and add them to the bootstrap list, then try to connect to each
curl -s https://www.zipp.org/node_list.txt | while read line; do
    if [[ ! -z "$line" ]] && [[ "$line" != "$current_id" ]]; then
        ipfs bootstrap add $line
        ipfs swarm connect $line
    fi
done

# Optional: Echo the bootstrap list to verify
echo "Current bootstrap list:"
ipfs bootstrap list

# Set trap for script exit
trap cleanup EXIT

# Start the Spring Boot application
java -jar /usr/app/app.jar