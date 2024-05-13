# ZIPP.org (Zone Internet Peer Protocol)

## Overview

ZIPP.org is an innovative project aimed at creating a decentralized incentivized private internet for hosting and sharing encrypted files securely. The project leverages the InterPlanetary File System (IPFS) to build a resilient and distributed network where users can participate as nodes to share encrypted data. The primary focus is on running zipped JavaScript applications directly within the browser, fostering a community-driven platform where developers and users can collaborate and exchange resources securely and efficiently.

## Key Features

- **Private IPFS Network**: Utilizes a customized IPFS setup to establish a private network where only authorized nodes participate, enhancing privacy and data integrity.
- **Encrypted File Sharing**: All files shared across the network are encrypted, ensuring data security and privacy. Files are only accessible to users who possess the appropriate decryption keys.
- **Incentive Mechanism**: Implements a token-based system where nodes earn tokens for hosting and sharing files. This incentivizes participants to contribute resources to the network.
- **Decentralized Architecture**: By operating on a peer-to-peer model, ZIPP.org reduces reliance on central servers, minimizing points of failure and resistance to censorship.
- **Browser-based Applications**: Focuses on running zipped JavaScript apps directly in the browser, allowing for seamless and efficient execution of applications without the need for additional software installation.

## Technology Stack

- **IPFS**: Provides the underlying technology for decentralized file storage and retrieval.
- **Java/Spring Boot**: Used for building the backend services, handling node communications, and integrating business logic.
- **JavaScript**: Facilitates the creation and execution of web-based applications shared within the network.
- **Encryption Technologies**: Utilizes AES for file encryption, ensuring robust security for stored data.

## Setup and Installation

1. **Prerequisites**:
   - Docker installed on your system.
   - Basic knowledge of Docker, IPFS, and network configuration.

2. **Building the Docker Image**:
   - Navigate to the project directory where the Dockerfile is located.
   - Run `docker build -t zipp-node .` to build the Docker image.

3. **Running the Node**:
   - Start your node using `docker run -p 4001:4001 -p 5001:5001 -p 8081:8081 -p 8082:8082 zipp-node`.
   - This command maps the necessary ports and starts the IPFS daemon and Java application.

4. **Joining the Network**:
   - Upon startup, your node will automatically connect to the ZIPP.org network by fetching a list of nodes from `www.zipp.org/node_list.txt` and establishing connections.

## Usage

- **Uploading Files**: Files can be uploaded through the web interface. Each file is encrypted before being added to IPFS.
- **Downloading Files**: Files can be retrieved and decrypted using the private keys held by the user.
- **Token Transactions**: Tokens are automatically managed by the system, with deductions for file uploads and rewards for hosting files.

## Contributing

ZIPP.org is an open-source project, and contributions are welcome. You can contribute in the following ways:
- **Development**: Help develop new features or improve existing functionalities.
- **Documentation**: Improve or expand the existing project documentation.
- **Testing**: Report bugs or provide feedback on user experience.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For more information, visit [www.zipp.org](https://www.zipp.org) or reach out via the contact form on our website. Join us in building a more secure and decentralized internet!

Created by [Lance](https://www.lance.name)