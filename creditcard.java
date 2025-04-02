package com.payment.transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CreditCardTransactionSystem {

    // Database configuration
    private static final String DB_URL = "jdbc:mysql://localhost:3306/payment_db";
    private static final String DB_USER = "payment_user";
    private static final String DB_PASSWORD = "strong_password_here";
    
    // Encryption constants
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY = "ThisIsASecretKeyForEncryption123";
    private static final String INIT_VECTOR = "EncryptionInitVec";
    
    // Connection pool
    private static Connection connection;
    
    /**
     * Main entry point for the application
     */
    public static void main(String[] args) {
        try {
            initializeDatabase();
            
            // Sample transaction
            CreditCardDetails card = new CreditCardDetails(
                "4111111111111111", 
                "John Doe", 
                "12/25", 
                "123"
            );
            
            processTransaction(card, 99.99, "Online Purchase");
            
        } catch (Exception e) {
            System.err.println("Transaction processing error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }
    
    /**
     * Initialize the database connection and tables
     */
    private static void initializeDatabase() throws SQLException {
        try {
            // Load the JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Create connection
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // Create tables if they don't exist
            createTables();
            
            System.out.println("Database connection established successfully");
            
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found: " + e.getMessage());
        }
    }
    
    /**
     * Create necessary database tables
     */
    private static void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        
        // Table for storing encrypted card details
        String createCardTable = "CREATE TABLE IF NOT EXISTS credit_cards ("
                + "id VARCHAR(36) PRIMARY KEY, "
                + "card_number_hash VARCHAR(64) NOT NULL, "
                + "card_number_encrypted TEXT NOT NULL, "
                + "cardholder_name_encrypted TEXT NOT NULL, "
                + "expiry_date_encrypted VARCHAR(255) NOT NULL, "
                + "last_four_digits VARCHAR(4) NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        
        // Table for storing transaction records
        String createTransactionTable = "CREATE TABLE IF NOT EXISTS transactions ("
                + "id VARCHAR(36) PRIMARY KEY, "
                + "credit_card_id VARCHAR(36) NOT NULL, "
                + "amount DECIMAL(10,2) NOT NULL, "
                + "description VARCHAR(255), "
                + "status VARCHAR(20) NOT NULL, "
                + "transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (credit_card_id) REFERENCES credit_cards(id))";
        
        stmt.execute(createCardTable);
        stmt.execute(createTransactionTable);
        stmt.close();
    }
    
    /**
     * Process a credit card transaction
     */
    public static boolean processTransaction(CreditCardDetails cardDetails, double amount, String description) 
            throws Exception {
        
        // Validate input data
        if (!validateCreditCard(cardDetails)) {
            throw new IllegalArgumentException("Invalid credit card details");
        }
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
        
        // Generate transaction ID
        String transactionId = UUID.randomUUID().toString();
        
        // Store or retrieve card information
        String cardId = storeCardDetails(cardDetails);
        
        // Process the actual payment (in a real system, this would connect to a payment gateway)
        boolean isApproved = processPaymentWithGateway(cardDetails, amount);
        
        // Store transaction record
        String status = isApproved ? "APPROVED" : "DECLINED";
        storeTransaction(transactionId, cardId, amount, description, status);
        
        System.out.println("Transaction " + transactionId + " processed with status: " + status);
        
        return isApproved;
    }
    
    /**
     * Store card details securely in the database
     */
    private static String storeCardDetails(CreditCardDetails card) throws Exception {
        // Check if card already exists by hashed number
        String cardNumberHash = hashCardNumber(card.getCardNumber());
        String lastFourDigits = card.getCardNumber().substring(card.getCardNumber().length() - 4);
        
        PreparedStatement checkStmt = connection.prepareStatement(
                "SELECT id FROM credit_cards WHERE card_number_hash = ?");
        checkStmt.setString(1, cardNumberHash);
        ResultSet rs = checkStmt.executeQuery();
        
        if (rs.next()) {
            // Card already exists, return its ID
            String existingId = rs.getString("id");
            rs.close();
            checkStmt.close();
            return existingId;
        }
        
        // Generate a new UUID for this card
        String cardId = UUID.randomUUID().toString();
        
        // Encrypt sensitive card data
        String encryptedCardNumber = encrypt(card.getCardNumber());
        String encryptedCardholderName = encrypt(card.getCardholderName());
        String encryptedExpiryDate = encrypt(card.getExpiryDate());
        
        // Store the encrypted data
        PreparedStatement insertStmt = connection.prepareStatement(
                "INSERT INTO credit_cards (id, card_number_hash, card_number_encrypted, "
                + "cardholder_name_encrypted, expiry_date_encrypted, last_four_digits) "
                + "VALUES (?, ?, ?, ?, ?, ?)");
        
        insertStmt.setString(1, cardId);
        insertStmt.setString(2, cardNumberHash);
        insertStmt.setString(3, encryptedCardNumber);
        insertStmt.setString(4, encryptedCardholderName);
        insertStmt.setString(5, encryptedExpiryDate);
        insertStmt.setString(6, lastFourDigits);
        
        insertStmt.executeUpdate();
        insertStmt.close();
        
        return cardId;
    }
    
    /**
     * Store transaction record
     */
    private static void storeTransaction(String id, String cardId, double amount, 
            String description, String status) throws SQLException {
        
        PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO transactions (id, credit_card_id, amount, description, status) "
                + "VALUES (?, ?, ?, ?, ?)");
        
        stmt.setString(1, id);
        stmt.setString(2, cardId);
        stmt.setDouble(3, amount);
        stmt.setString(4, description);
        stmt.setString(5, status);
        
        stmt.executeUpdate();
        stmt.close();
    }
    
    /**
     * Process payment through external payment gateway
     * This is a simplified simulation - in a real system, this would connect to an actual payment processor
     */
    private static boolean processPaymentWithGateway(CreditCardDetails card, double amount) {
        // Simulate gateway processing
        try {
            System.out.println("Connecting to payment gateway...");
            Thread.sleep(1000); // Simulate processing time
            
            // Simple validation - in reality, would get response from payment gateway
            boolean approved = !card.getCardNumber().equals("4111111111111112"); // Deny a specific test card
            
            System.out.println("Payment gateway response received");
            return approved;
            
        } catch (InterruptedException e) {
            System.err.println("Payment gateway connection interrupted");
            return false;
        }
    }
    
    /**
     * Create a secure hash of the card number for indexing without storing the actual number
     */
    private static String hashCardNumber(String cardNumber) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(cardNumber.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    /**
     * Encrypt sensitive data with AES encryption
     */
    private static String encrypt(String value) throws Exception {
        IvParameterSpec iv = new IvParameterSpec(INIT_VECTOR.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
        
        byte[] encrypted = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }
    
    /**
     * Decrypt data using AES encryption
     */
    private static String decrypt(String encrypted) throws Exception {
        IvParameterSpec iv = new IvParameterSpec(INIT_VECTOR.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        
        byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));
        return new String(original);
    }
    
    /**
     * Validate credit card details
     */
    private static boolean validateCreditCard(CreditCardDetails card) {
        // Check card number using Luhn algorithm
        if (!isValidLuhn(card.getCardNumber())) {
            return false;
        }
        
        // Check expiration date is in future
        if (!isValidExpiryDate(card.getExpiryDate())) {
            return false;
        }
        
        // Check CVV format
        if (!isValidCVV(card.getCvv())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate credit card number using Luhn algorithm
     */
    private static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 13 || cardNumber.length() > 19) {
            return false;
        }
        
        // Remove any non-digit characters
        cardNumber = cardNumber.replaceAll("\\D", "");
        
        int sum = 0;
        boolean alternate = false;
        
        // Process from right to left
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.digit(cardNumber.charAt(i), 10);
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = digit - 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
    
    /**
     * Validate expiry date format and ensure it's in the future
     */
    private static boolean isValidExpiryDate(String expiryDate) {
        if (expiryDate == null || !expiryDate.matches("\\d{2}/\\d{2}")) {
            return false;
        }
        
        String[] parts = expiryDate.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]) + 2000; // Assuming format is MM/YY
        
        if (month < 1 || month > 12) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        
        // Check if card is expired
        return (year > currentYear || (year == currentYear && month >= currentMonth));
    }
    
    /**
     * Validate CVV format (3-4 digits)
     */
    private static boolean isValidCVV(String cvv) {
        return cvv != null && cvv.matches("\\d{3,4}");
    }
    
    /**
     * Close database connection
     */
    private static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
    
    /**
     * Credit card details class
     */
    public static class CreditCardDetails {
        private final String cardNumber;
        private final String cardholderName;
        private final String expiryDate;
        private final String cvv;
        
        public CreditCardDetails(String cardNumber, String cardholderName, String expiryDate, String cvv) {
            this.cardNumber = cardNumber;
            this.cardholderName = cardholderName;
            this.expiryDate = expiryDate;
            this.cvv = cvv;
        }
        
        public String getCardNumber() {
            return cardNumber;
        }
        
        public String getCardholderName() {
            return cardholderName;
        }
        
        public String getExpiryDate() {
            return expiryDate;
        }
        
        public String getCvv() {
            return cvv;
        }
    }
}