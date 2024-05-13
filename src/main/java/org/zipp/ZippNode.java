// This file is the property of Zipp.org
// Created by Lance - www.lance.name

package org.zipp;

import java.net.HttpURLConnection;
import java.net.URL;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.api.Peer;
import io.ipfs.multihash.Multihash;
import io.ipfs.multiaddr.MultiAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletResponse;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKey;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

class SharedFile implements Serializable {
	private String fileHash;
	private String fileName;
	private long fileSize;

	public SharedFile(String fileHash, String fileName, long fileSize) {
		this.fileHash = fileHash;
		this.fileName = fileName;
		this.fileSize = fileSize;
	}

	public String getFileHash() {
		return fileHash;
	}

	public String getFileName() {
		return fileName;
	}

	public long getFileSize() {
		return fileSize;
	}
}

class Wallet implements Serializable {
	private String address;
	private int balance;

	public Wallet(String address, int initialBalance) {
		this.address = address;
		this.balance = initialBalance;
	}

	public boolean deductTokens(int amount) {
		if (amount <= balance) {
			balance -= amount;
			return true;
		}
		return false;
	}

	public void addTokens(int amount) {
		balance += amount;
	}

	// Getters
	public String getAddress() {
		return address;
	}

	public int getBalance() {
		return balance;
	}
}

@Component
class AppRunner implements CommandLineRunner {

	private final ZippNode zippNode;

	@Autowired
	public AppRunner(ZippNode zippNode) {
		this.zippNode = zippNode;
	}

	@Override
	public void run(String... args) throws Exception {
		zippNode.checkConnectedPeers();
	}
}

@SpringBootApplication
public class ZippNode {
	private final transient IPFS ipfs;
	private final int port;
	private Wallet nodeWallet;
	private static final int TOKEN_COST_PER_FILE = 1;
	private static final String ENCRYPTION_ALGORITHM = "AES";

	// Inside your ZippNode class
	private static final String SHARED_FILES_FILE = "/usr/app/shared_files.dat";
	private SecretKeySpec encryptionKey;

	private ServerSocket serverSocket;
	private ExecutorService serverExecutor;

	private transient List<SharedFile> sharedFiles = new ArrayList<>();

	public ZippNode(@Value("${zipp.node.port}") int port) {
		this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
		this.port = port;
		initializeWallet();
		loadSharedFiles();
	}

	private void saveSharedFiles() {
		try (FileOutputStream fos = new FileOutputStream(SHARED_FILES_FILE);
				ObjectOutputStream oos = new ObjectOutputStream(fos)) {
			oos.writeObject(sharedFiles);
			System.out.println("Shared files saved successfully.");
		} catch (IOException e) {
			System.err.println("Error saving shared files: " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void loadSharedFiles() {
		File file = new File(SHARED_FILES_FILE);
		if (!file.exists() || file.length() == 0) {
			System.out.println("No existing shared files to load, or file is empty.");
			return;
		}
		try (FileInputStream fis = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fis)) {
			sharedFiles = (List<SharedFile>) ois.readObject();
			System.out.println("Shared files loaded successfully.");
		} catch (IOException | ClassNotFoundException e) {
			System.err.println("Error loading shared files: " + e.getMessage());
		}
	}

	// Method to set master password from WebUI
	public void setMasterPassword(char[] password) throws NoSuchAlgorithmException {
		this.encryptionKey = deriveKeyFromMasterPassword(password);
		Arrays.fill(password, ' '); // Overwrite password in memory
	}

	// Method to manually start server
	public void initializeAndStartServer() {
		if (this.encryptionKey == null) {
			throw new IllegalStateException("Master password must be set before starting the server.");
		}
		loadSharedFiles(); // Load the shared files from storage
		startServer();
	}

	private byte[] encryptFile(byte[] fileData) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
		return cipher.doFinal(fileData);
	}

	private byte[] decryptFile(byte[] encryptedData) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
		return cipher.doFinal(encryptedData);
	}

	private SecretKeySpec deriveKeyFromMasterPassword(char[] masterPassword) throws NoSuchAlgorithmException {
		String algorithm = "PBKDF2WithHmacSHA256";
		int keyLength = 256; // 256-bit for AES
		int iterations = 200_000; // Iteration count - adjust for security vs. performance

		byte[] salt = "some-fixed-salt".getBytes(); // Use a fixed salt for simplicity

		PBEKeySpec keySpec = new PBEKeySpec(masterPassword, salt, iterations, keyLength);
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm);
		try {
			SecretKey secretKey = keyFactory.generateSecret(keySpec);
			return new SecretKeySpec(secretKey.getEncoded(), "AES");
		} catch (InvalidKeySpecException e) {
			// Handle these exceptions appropriately (logging, etc.)
			throw new RuntimeException("Error deriving key from password", e);
		}
	}

	private void initializeWallet() {
		// For simplicity, initializing wallet with a fixed number of tokens
		nodeWallet = new Wallet(null, 100); // Example: start with 100 tokens
		// In a real application, you might load the wallet data from a file or database
	}

	private void startServer() {
		serverExecutor = Executors.newSingleThreadExecutor();
		serverExecutor.execute(() -> {
			try {
				serverSocket = new ServerSocket(port);
				System.out.println("Node started on port " + port);
				while (!serverSocket.isClosed()) {
					Socket clientSocket = serverSocket.accept();
					Executors.newCachedThreadPool().execute(() -> handleNodeCommunication(clientSocket));
				}
			} catch (IOException e) {
				if (!serverSocket.isClosed()) {
					System.err.println("Server error: " + e.getMessage());
				} else {
					System.out.println("Server stopped.");
				}
			}
		});
	}

	// Method to stop the server
	public void stopServer() {
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close(); // This will break the server loop by throwing a SocketException
			}
			if (serverExecutor != null && !serverExecutor.isShutdown()) {
				serverExecutor.shutdown(); // Attempt to shutdown the executor service
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean deductToken(int amount) {
		return nodeWallet.deductTokens(amount);
	}

	private void handleNodeCommunication(Socket clientSocket) {
		try (Socket socket = clientSocket; // Automatic resource closing
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

			String messageType = in.readLine();

			System.out.println(clientSocket.getInetAddress().getHostAddress() + ":" + messageType);
			if (messageType == null)
				return;

			// Implement other message types and their handling

		} catch (IOException e) {
			System.err.println("Error handling node communication: " + e.getMessage());
		}
	}

	private String addFileToIPFS(String filePath) {
		File file = new File(filePath);
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] fileData = new byte[(int) file.length()];
			fis.read(fileData);
			byte[] encryptedData = encryptFile(fileData);

			if (deductToken(TOKEN_COST_PER_FILE)) {
				NamedStreamable.ByteArrayWrapper fileWrapper = new NamedStreamable.ByteArrayWrapper(encryptedData);
				MerkleNode addResult = ipfs.add(fileWrapper).get(0);
				String fileHash = addResult.hash.toBase58();

				// Pin the file to ensure it remains on your node
				ipfs.pin.add(addResult.hash);

				SharedFile sharedFile = new SharedFile(fileHash, file.getName(), file.length());
				sharedFiles.add(sharedFile);
				saveSharedFiles();

				return fileHash;
			} else {
				System.err.println("Insufficient tokens to store file.");
				return null;
			}
		} catch (IOException | GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}
	}

	public byte[] retrieveFileFromIPFS(SharedFile sharedFile) throws GeneralSecurityException {
		String fileHash = sharedFile.getFileHash();
		try {
			Multihash fileMultihash = Multihash.fromBase58(fileHash);
			byte[] encryptedData = ipfs.cat(fileMultihash);
			return decryptFile(encryptedData);
		} catch (IOException e) {
			System.err.println("File not found locally. Attempting to fetch from network: " + e.getMessage());
			try {
				// Convert the fileHash (String) to a Multihash object
				Multihash fileMultihash = Multihash.fromBase58(fileHash);
				
				// Attempt to fetch the file from the IPFS network
				byte[] encryptedData = ipfs.get(fileMultihash);
				if (encryptedData != null) {
					// Pin the fetched file to ensure it remains available locally
					ipfs.pin.add(fileMultihash);
					return decryptFile(encryptedData);
				} else {
					throw new RuntimeException("File not found in the IPFS network");
				}
			} catch (IOException ex) {
				throw new RuntimeException("Error retrieving file from IPFS network", ex);
			}
		}
	}

	public List<SharedFile> getSharedFiles() {
		return sharedFiles;
	}

	@Service
	public static class ServerService {
		private boolean serverRunning = false;

		public void startServer() {
			serverRunning = true;
		}

		public void stopServer() {
			serverRunning = false;
		}

		public boolean isServerRunning() {
			return serverRunning;
		}
	}

	@Controller
	public static class ServerController {

		@Autowired
		private ServerService serverService;

		@Autowired
		private ZippNode zippNode; // Autowire ZippNode

		private static final Logger logger = LoggerFactory.getLogger(ServerController.class);

		@GetMapping("/")
		public String index(Model model) {
			logger.info("Home page requested");
			model.addAttribute("serverRunning", serverService.isServerRunning());
			model.addAttribute("sharedFiles", zippNode.getSharedFiles());
			return "index";
		}

		@PostMapping("/start-server")
		public String startServer() {
			zippNode.initializeAndStartServer();
			serverService.startServer();
			return "redirect:/";
		}

		@PostMapping("/stop-server")
		public String stopServer() {
			zippNode.stopServer();
			serverService.stopServer();
			return "redirect:/";
		}

		@PostMapping("/download-file")
		public void downloadFile(@RequestParam("fileHash") String fileHash, HttpServletResponse response)
				throws IOException {
			Optional<SharedFile> sharedFile = zippNode.getSharedFiles().stream()
					.filter(file -> file.getFileHash().equals(fileHash))
					.findFirst();

			if (sharedFile.isPresent()) {
				try {
					byte[] fileData = zippNode.retrieveFileFromIPFS(sharedFile.get());
					response.setContentType("application/octet-stream");
					response.setHeader("Content-Disposition",
							"attachment; filename=\"" + sharedFile.get().getFileName() + "\"");
					response.getOutputStream().write(fileData);
				} catch (GeneralSecurityException e) {
					response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					response.getWriter().write("Error retrieving file: " + e.getMessage());
				}
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				response.getWriter().write("File not found");
			}
		}

		@PostMapping("/upload-file")
		public String handleFileUpload(@RequestParam("file") MultipartFile file) {
			if (file.isEmpty()) {
				System.err.println("Uploaded file is empty.");
				return "redirect:/?error=empty";
			}

			// Convert MultipartFile to File
			File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
			try {
				convFile.createNewFile();
				try (FileOutputStream fos = new FileOutputStream(convFile)) {
					fos.write(file.getBytes());
				}

				// Now you can call addFileToIPFS
				String fileHash = zippNode.addFileToIPFS(convFile.getAbsolutePath());
				if (fileHash != null) {
					System.out.println("File added to IPFS with hash: " + fileHash);
				} else {
					System.err.println("File could not be added to IPFS.");
					return "redirect:/?error=ipfs";
				}
			} catch (IOException e) {
				System.err.println("Error converting uploaded file: " + e.getMessage());
				return "redirect:/?error=conversion";
			} finally {
				// Cleanup: Delete the temporary file
				if (convFile.exists()) {
					convFile.delete();
				}
			}

			return "redirect:/";
		}

		@PostMapping("/set-master-password")
		public String setMasterPassword(@RequestParam("password") String password) throws NoSuchAlgorithmException {
			zippNode.setMasterPassword(password.toCharArray());
			return "redirect:/";
		}
	}

	public void loadAndConnectPeers() {
		try {
			URL url = new URL("https://www.zipp.org/node_list.txt");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isEmpty()) {
					try {
						MultiAddress address = new MultiAddress(line);
						System.out.println("Adding and connecting to peer: " + line);
						ipfs.bootstrap.add(address); // Add to bootstrap list
						ipfs.swarm.connect(address); // Attempt to connect
					} catch (IllegalArgumentException e) {
						System.err.println("Invalid multiaddress: " + line + " | Error: " + e.getMessage());
					}
				}
			}
			reader.close();
		} catch (Exception e) {
			System.err.println("Error loading or connecting to peers: " + e.getMessage());
		}
	}

	public int checkPeersViaHttpApi() {
		HttpURLConnection conn = null;
		BufferedReader reader = null;
		int peerCount = 0; // Variable to store the number of connected peers

		try {
			URL url = new URL("http://127.0.0.1:5001/api/v0/swarm/peers");
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true); // Allows sending a request body, necessary for POST

			// Send an empty body to POST request
			try (OutputStream os = conn.getOutputStream()) {
				byte[] input = new byte[0];
				os.write(input, 0, input.length);
			}

			// Check the response and read the data
			if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}

				// Parse JSON response to count peers
				JSONObject jsonObject = new JSONObject(response.toString());
				JSONArray peers = jsonObject.getJSONArray("Peers");
				peerCount = peers.length(); // Count the number of peers in the response
			} else {
				System.out.println("HTTP error code: " + conn.getResponseCode());
			}
		} catch (Exception e) {
			System.err.println("Error querying peers via HTTP API: " + e.getMessage());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
		return peerCount; // Return the count of connected peers
	}

	public void checkConnectedPeers() {
		try {
			int peers = checkPeersViaHttpApi();
			System.out.println("Connected peers: " + peers);
			if (peers == 0) {
				System.out.println("No peers connected according to Java app. Trying to load and connect to peers...");
				loadAndConnectPeers();
				List<Peer> refreshedPeers = ipfs.swarm.peers();
				System.out.println("Rechecked connected peers: " + refreshedPeers.size());
				for (Peer peer : refreshedPeers) {
					System.out.println("Connected to: " + peer.toString());
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to fetch connected peers: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(ZippNode.class, args);
	}
}